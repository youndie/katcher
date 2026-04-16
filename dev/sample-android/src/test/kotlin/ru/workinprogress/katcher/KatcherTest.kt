package ru.workinprogress.katcher

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class KatcherTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        resetKatcherState()
    }

    @After
    fun tearDown() {
        val dir = File(context.filesDir, "katcher_crashes")
        dir.deleteRecursively()
    }

    @Test
    fun `start correctly initializes config and clears breadcrumbs`() {
        Katcher.addBreadcrumb("Old breadcrumb")

        Katcher.start(context, "test-uuid") {
            apiUrl = "https://katcher.test/"
            appKey = "test-key"
            isDebug = true
        }

        val config = getPrivateField("config") as Katcher.KatcherConfig
        assertEquals("https://katcher.test", config.apiUrl)
        assertEquals("test-uuid", getPrivateField("buildUuid"))

        val breadcrumbs = getPrivateField("breadcrumbs") as List<*>
        assertTrue("Breadcrumbs should be empty after start", breadcrumbs.isEmpty())
    }

    @Test
    fun `addBreadcrumb enforces size limit and stores data`() {
        for (i in 1..51) {
            Katcher.addBreadcrumb(message = "Message $i", type = "log")
        }

        val breadcrumbs = getPrivateField("breadcrumbs") as List<*>
        assertEquals("Should not exceed MAX_BREADCRUMBS", 50, breadcrumbs.size)

        val lastBreadcrumb = breadcrumbs.last()!!

        val message = lastBreadcrumb.javaClass.getDeclaredMethod("getMessage").invoke(lastBreadcrumb)
        assertEquals("Message 51", message)
    }

    @Test
    fun `handleCrash creates valid JSON file in crash directory`() {
        Katcher.start(context, "test-uuid-123") {
            apiUrl = "http://localhost"
            appKey = "test-key"
            environment = "testing"
        }

        Katcher.addBreadcrumb("User clicked button")

        val handleCrashMethod =
            Katcher::class.java.getDeclaredMethod(
                "handleCrash",
                Context::class.java,
                Throwable::class.java,
            )
        handleCrashMethod.isAccessible = true

        val testException = RuntimeException("Test Exception Message")

        handleCrashMethod.invoke(Katcher, context, testException)

        val crashDir = File(context.filesDir, "katcher_crashes")
        assertTrue("Crash directory should exist", crashDir.exists())

        val crashFiles = crashDir.listFiles()
        assertNotNull(crashFiles)
        assertEquals("Should contain exactly 1 crash file", 1, crashFiles?.size)

        val savedFile = crashFiles!!.first()
        val jsonString = savedFile.readText()
        val json = JSONObject(jsonString)

        assertEquals("test-key", json.getString("appKey"))
        assertEquals("Test Exception Message", json.getString("message"))
        assertEquals("testing", json.getString("environment"))

        val breadcrumbsArray = json.getJSONArray("breadcrumbs")
        assertEquals(1, breadcrumbsArray.length())
        assertEquals("User clicked button", breadcrumbsArray.getJSONObject(0).getString("message"))
    }

    @Test
    fun `flushStoredCrashes deletes files after successful sending`() {
        val crashDir = File(context.filesDir, "katcher_crashes")
        crashDir.mkdirs()
        val crashFile = File(crashDir, "old_crash.json")
        val json =
            JSONObject().apply {
                put("appKey", "test-key")
                put("message", "Old crash")
            }
        crashFile.writeText(json.toString())

        Katcher.start(context, "test-uuid") {
            apiUrl = "http://invalid-url-that-causes-exception"
            appKey = "test-key"
        }

        val flushMethod = Katcher::class.java.getDeclaredMethod("flushStoredCrashes", Context::class.java)
        flushMethod.isAccessible = true
        flushMethod.invoke(Katcher, context)

        Thread.sleep(500)

        assertTrue("File should still exist after network error", crashFile.exists())
    }

    @Test
    fun `saveCrashToDisk creates file with correct content`() {
        val filename = "test_save.json"
        val json = JSONObject().apply { put("test", "data") }

        val saveMethod =
            Katcher::class.java.getDeclaredMethod(
                "saveCrashToDisk",
                Context::class.java,
                String::class.java,
                JSONObject::class.java,
            )
        saveMethod.isAccessible = true
        saveMethod.invoke(Katcher, context, filename, json)

        val file = File(File(context.filesDir, "katcher_crashes"), filename)
        assertTrue("File should be saved", file.exists())
        assertEquals(json.toString(), file.readText())
    }

    private fun getPrivateField(fieldName: String): Any? {
        val field = Katcher::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(Katcher)
    }

    private fun resetKatcherState() {
        // Сбрасываем флаг isCrashing
        val isCrashingField = Katcher::class.java.getDeclaredField("isCrashing")
        isCrashingField.isAccessible = true
        (isCrashingField.get(Katcher) as AtomicBoolean).set(false)

        Katcher.clearBreadcrumbs()
    }
}
