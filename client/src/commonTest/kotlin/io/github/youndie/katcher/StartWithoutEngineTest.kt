package io.github.youndie.katcher

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-03: a consumer that links no Ktor engine used to start, keep every report on disk and retry it
 * for ever, with the reason printed only under `isDebug`. The client's own test classpath links no
 * engine either, which is what makes this test possible here.
 */
class StartWithoutEngineTest {
    @Test
    fun `test start refuses when no HTTP engine is linked`() {
        assertTrue(runCatching { HttpClient() }.isFailure, "premise: this test's classpath must link no engine")

        Katcher.start {
            appKey = "FAKE-key"
            remoteHost = "http://127.0.0.1:9"
        }

        assertFalse(Katcher.isStarted, "a reporter that cannot send must not look started")
    }
}
