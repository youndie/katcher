package ru.workinprogress.katcher

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object Katcher {
    private const val TAG = "Katcher"
    private const val CRASH_DIR = "katcher_crashes"

    private val isCrashing = AtomicBoolean(false)

    private val executor = Executors.newSingleThreadExecutor()

    private var config: KatcherConfig? = null
    private var buildUuid: String? = null
    private var appVersion: String = "unknown"

    data class KatcherConfig(
        var apiUrl: String,
        var appKey: String,
        var environment: String = "production",
        var isDebug: Boolean = false,
    )

    fun start(
        context: Context,
        buildUuid: String,
        configBuilder: KatcherConfig.() -> Unit,
    ) {
        val cfg = KatcherConfig("", "").apply(configBuilder)
        val validUrl = if (cfg.apiUrl.endsWith("/")) cfg.apiUrl.dropLast(1) else cfg.apiUrl

        this.config = cfg.copy(apiUrl = validUrl)
        this.buildUuid = buildUuid

        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            this.appVersion = pInfo.versionName ?: "0.0"
        } catch (e: Exception) {
            if (cfg.isDebug) Log.w(TAG, "Could not resolve app version", e)
        }

        setupUncaughtExceptionHandler(context.applicationContext)
        flushStoredCrashes(context.applicationContext)

        if (cfg.isDebug) Log.d(TAG, "Initialized. Build: $buildUuid, Ver: $appVersion")
    }

    private fun setupUncaughtExceptionHandler(appContext: Context) {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(appContext, throwable)
            oldHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun handleCrash(
        context: Context,
        throwable: Throwable,
    ) {
        if (!isCrashing.compareAndSet(false, true)) return

        try {
            val config = this.config ?: return

            val reportJson =
                JSONObject().apply {
                    put("appKey", config.appKey)
                    put("message", throwable.message ?: throwable.javaClass.simpleName)
                    put("stacktrace", Log.getStackTraceString(throwable))
                    put("release", appVersion) // Используем реальную версию из PackageManager
                    put("environment", config.environment)

                    val contextData =
                        JSONObject().apply {
                            put("os", "Android")
                            put("os_version", Build.VERSION.RELEASE)
                            put("sdk", Build.VERSION.SDK_INT.toString())
                            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                            put("brand", Build.BRAND)
                            put("build_uuid", buildUuid)
                        }

                    put("context", contextData)
                }

            if (config.isDebug) {
                Log.d(TAG, "Generated Report JSON: $reportJson")
            }
            val filename = "crash_${System.currentTimeMillis()}.json"
            saveCrashToDisk(context, filename, reportJson)

            sendNetworkRequest(reportJson) { success ->
                if (success) {
                    deleteCrashFile(context, filename)
                }
            }

            Thread.sleep(500)
        } catch (e: Exception) {
            if (config?.isDebug == true) Log.e(TAG, "Failed to handle crash", e)
        } finally {
            isCrashing.set(false)
        }
    }

    private fun getCrashDir(context: Context): File {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun saveCrashToDisk(
        context: Context,
        filename: String,
        json: JSONObject,
    ) {
        try {
            val file = File(getCrashDir(context), filename)
            file.writeText(json.toString())
            if (config?.isDebug == true) Log.d(TAG, "Crash saved to disk: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash to disk", e)
        }
    }

    private fun deleteCrashFile(
        context: Context,
        filename: String,
    ) {
        try {
            File(getCrashDir(context), filename).delete()
        } catch (_: Exception) {
        }
    }

    private fun flushStoredCrashes(context: Context) {
        executor.execute {
            try {
                val dir = getCrashDir(context)
                val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: return@execute

                if (files.isNotEmpty() && config?.isDebug == true) {
                    Log.d(TAG, "Found ${files.size} unsent crashes. Sending...")
                }

                files.forEach { file ->
                    try {
                        val content = file.readText()
                        val json = JSONObject(content)
                        sendNetworkRequest(json, isAsync = false) { success ->
                            if (success) file.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to flush crash: ${file.name}", e)
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Flush error", e)
            }
        }
    }

    private fun sendNetworkRequest(
        json: JSONObject,
        isAsync: Boolean = true,
        onResult: (Boolean) -> Unit = {},
    ) {
        val config = this.config ?: return

        val task =
            Runnable {
                var success = false
                try {
                    val url = URL("${config.apiUrl}/api/reports")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doOutput = true
                    conn.doInput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                    conn.outputStream.use { os ->
                        BufferedWriter(OutputStreamWriter(os, "UTF-8")).use { it.write(json.toString()) }
                    }

                    val code = conn.responseCode
                    success = code in 200..299

                    if (config.isDebug) Log.d(TAG, "Crash upload result: $code")
                    conn.disconnect()
                } catch (e: Exception) {
                    if (config.isDebug) Log.e(TAG, "Network error", e)
                } finally {
                    onResult(success)
                }
            }

        if (isAsync) {
            Thread(task).start()
        } else {
            task.run()
        }
    }
}
