package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.Breadcrumb
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What `Katcher.record` hands to the disk: the path every caught and fatal crash takes. */
class SaveCrashTest {
    private val botUrl = "https://api.example.org/bot123456:FAKE-test-secret/getUpdates"
    private val dbUrl = "postgres://user:FAKE-pass@db:5432/x"

    @Test
    fun `test no credential reaches the stored report`() {
        val storage = InMemoryKatcherFileSystem()

        storage.saveCrash(
            throwable = IllegalStateException("request to $botUrl failed; fallback $dbUrl refused"),
            context = mapOf("endpoint" to botUrl),
            config = KatcherConfig(appKey = "FAKE-app-key"),
            breadcrumbs =
                listOf(
                    Breadcrumb(
                        timestamp = LocalDateTime(2026, 11, 9, 12, 0),
                        type = "http",
                        message = "GET $botUrl",
                        data = mapOf("db" to dbUrl),
                    ),
                ),
        )

        val stored = storage.getReports().single().params
        val written = Katcher.json.encodeToString(stored)
        assertFalse("FAKE-test-secret" in written, written)
        assertFalse("FAKE-pass" in written, written)
        assertTrue("/getUpdates" in stored.stacktrace, stored.stacktrace)
        assertTrue("db:5432" in stored.message, stored.message)
        assertEquals("FAKE-app-key", stored.appKey)
    }
}
