package ru.workinprogress.katcher

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.report.CreateReportParams

internal expect fun setupPlatformHandler()

object Katcher {
    private val isCrashing = atomic(false)
    private var config: KatcherConfig = KatcherConfig()
    private const val LOGO = """📡"""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val uploadSignal = Channel<Unit>(Channel.CONFLATED)

    internal val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 7_000
                connectTimeoutMillis = 3_000
                socketTimeoutMillis = 5_000
            }
            install(HttpRequestRetry) {
                maxRetries = 2
                retryIf { _, response -> !response.status.isSuccess() }
                retryOnExceptionIf { _, _ -> true }
                exponentialDelay(maxDelayMs = 3_000)
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                url { takeFrom(config.remoteHost) }
            }
        }
    }

    fun start(configure: KatcherConfig.() -> Unit) {
        val newConfig = KatcherConfig().apply(configure)

        if (newConfig.appKey.isEmpty() || newConfig.remoteHost.isEmpty()) {
            println("$LOGO Configuration error: appKey and remoteHost are required.")
            return
        }
        config = newConfig
        setupPlatformHandler()

        scope.launch {
            processQueue()
        }

        uploadSignal.trySend(Unit)

        if (config.isDebug) println("$LOGO Katcher initialized. Storage ready.")
    }

    fun catch(
        throwable: Throwable,
        context: Map<String, String> = emptyMap(),
    ) {
        if (config.appKey.isEmpty()) return

        val first = isCrashing.compareAndSet(expect = false, update = true)
        if (!first) return

        try {
            val params =
                CreateReportParams(
                    appKey = config.appKey,
                    message = throwable.message.toString(),
                    stacktrace = throwable.stackTraceToString(),
                    release = config.release,
                    environment = config.environment,
                    context = context,
                )

            fileSystem.saveReport(params)

            if (config.isDebug) println("$LOGO Report saved to disk. Signal sent.")

            uploadSignal.trySend(Unit)
        } catch (e: Exception) {
            println("$LOGO Failed to save crash report: ${e.message}")
        } finally {
            isCrashing.value = false
        }
    }

    private suspend fun processQueue() {
        for (signal in uploadSignal) {
            if (config.isDebug) println("$LOGO Worker woke up. Checking disk...")

            val reports = fileSystem.getReports()

            for (report in reports) {
                val success = sendReport(report.params)

                if (success) {
                    fileSystem.deleteReport(report.fileName)
                } else {
                    if (config.isDebug) println("$LOGO Network error. Retrying later.")
                    break
                }
            }
        }
    }

    private suspend fun sendReport(params: CreateReportParams): Boolean =
        try {
            val response: HttpResponse =
                httpClient.post("api/reports") {
                    setBody(params)
                }
            if (response.status.isSuccess()) {
                if (config.isDebug) println("$LOGO Sent: ${params.message.take(20)}...")
                true
            } else {
                if (config.isDebug) println("$LOGO Server rejected: ${response.status}")
                false
            }
        } catch (e: Exception) {
            if (config.isDebug) println("$LOGO Transmission failed: ${e.message}")
            false
        }
}
