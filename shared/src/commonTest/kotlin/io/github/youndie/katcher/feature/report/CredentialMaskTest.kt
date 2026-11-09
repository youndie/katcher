package io.github.youndie.katcher.feature.report

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialMaskTest {
    private fun mask(text: String) = CredentialMask.mask(text)

    @Test
    fun `a bot API token in a URL path is masked and the method kept`() {
        val masked =
            mask(
                "ClientRequestException: Client request(GET https://api.example.org/bot123456:FAKE-test-secret/getUpdates) invalid",
            )

        assertFalse("FAKE-test-secret" in masked, masked)
        assertFalse("123456" in masked, masked)
        assertTrue("https://api.example.org/bot<redacted>/getUpdates" in masked, masked)
    }

    @Test
    fun `the userinfo of a URL is masked and the host and port kept`() {
        assertEquals(
            "PSQLException: cannot connect to postgres://<redacted>@db:5432/x",
            mask("PSQLException: cannot connect to postgres://user:FAKE-pass@db:5432/x"),
        )
    }

    @Test
    fun `query parameters named like credentials are masked and others kept`() {
        assertEquals(
            "GET https://h.example/cb?code=42&access_token=<redacted>&page=2&X-Amz-Signature=<redacted>",
            mask("GET https://h.example/cb?code=42&access_token=FAKE-tok&page=2&X-Amz-Signature=FAKE-sig"),
        )
        assertEquals("?api_key=<redacted>&apiKey=<redacted>&key=<redacted>", mask("?api_key=a1&apiKey=b2&key=c3"))
    }

    @Test
    fun `a parameter whose name only contains a secret word is not a secret`() {
        val text = "?monkey=1&keyboard=2&tokens_left=3"
        assertEquals(text, mask(text))
    }

    @Test
    fun `a data class toString is masked field by field`() {
        assertEquals(
            "Config(host=db, password=<redacted>, apiKey=<redacted>)",
            mask("Config(host=db, password=FAKE-pass, apiKey=FAKE-key)"),
        )
    }

    @Test
    fun `an authorization header value is masked and its scheme kept`() {
        assertEquals("Authorization: Bearer <redacted>", mask("Authorization: Bearer FAKE-bearer"))
        assertEquals("headers=[Authorization=[Basic <redacted>]]", mask("headers=[Authorization=[Basic RkFLRTpGQUtF]]"))
        assertEquals("authorization: <redacted>", mask("authorization: FAKE-raw"))
    }

    @Test
    fun `a JWT is masked wherever it stands`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJGQUtFIn0.FAKEsignature_-"
        assertEquals("token rejected: <redacted> expired", mask("token rejected: $jwt expired"))
    }

    @Test
    fun `masking twice gives what masking once gave`() {
        val inputs =
            listOf(
                "https://api.example.org/bot123456:FAKE-test-secret/getUpdates",
                "postgres://user:FAKE-pass@db:5432/x",
                "?token=FAKE&sig=FAKE",
                "Authorization: Bearer FAKE",
                "Config(password=FAKE)",
                "eyJa.eyJb.c",
            )
        inputs.forEach { input ->
            val once = mask(input)
            assertEquals(once, mask(once), input)
        }
    }

    @Test
    fun `a stack trace without credentials comes back unchanged`() {
        val trace =
            """
            java.lang.IllegalStateException: account 9f8e7d6c5b4a not found at 0x7ffd5e8c
            	at com.acme.billing.AccountRepository.load(AccountRepository.kt:64)
            	at 3   app.kexe   0x000000010012ab34 kfun:com.acme#main(){} + 52
            	at java.base/java.lang.Thread.run(Thread.java:1583)
            """.trimIndent()
        assertEquals(trace, mask(trace))
    }

    @Test
    fun `a report is masked in every text field and keeps its app key`() {
        val secret = "https://api.example.org/bot123456:FAKE-test-secret/getUpdates"
        val report =
            CreateReportParams(
                appKey = "FAKE-app-key-123",
                message = secret,
                stacktrace = "IOException: $secret",
                context = mapOf("url" to secret),
                breadcrumbs =
                    listOf(
                        Breadcrumb(
                            timestamp = LocalDateTime(2026, 11, 9, 12, 0),
                            type = "http",
                            message = secret,
                            data = mapOf("url" to secret),
                        ),
                    ),
            ).maskCredentials()

        assertEquals("FAKE-app-key-123", report.appKey)
        assertFalse("FAKE-test-secret" in report.toString(), report.toString())
        assertTrue(report.stacktrace.endsWith("/bot<redacted>/getUpdates"))
    }
}
