package io.github.youndie.katcher.feature.report

import io.github.youndie.katcher.KatcherProbes
import io.github.youndie.katcher.ServerConfig
import io.github.youndie.katcher.data.RepositoryTest
import io.github.youndie.katcher.db.AppsCrudRepositoryImpl
import io.github.youndie.katcher.db.ErrorGroupCrudRepositoryImpl
import io.github.youndie.katcher.db.SymbolMapCrudRepositoryImpl
import io.github.youndie.katcher.feature.app.AppType
import io.github.youndie.katcher.feature.app.data.AppRepositoryImpl
import io.github.youndie.katcher.feature.auth.X_AUTH_REQUEST_EMAIL
import io.github.youndie.katcher.feature.auth.X_AUTH_REQUEST_USER
import io.github.youndie.katcher.feature.error.ProcessReportUseCase
import io.github.youndie.katcher.feature.error.data.ErrorGroupRepositoryImpl
import io.github.youndie.katcher.feature.error.data.ErrorGroupViewedRepositoryImpl
import io.github.youndie.katcher.feature.report.data.ReportRepositoryImpl
import io.github.youndie.katcher.feature.symbolication.SymbolicationService
import io.github.youndie.katcher.feature.symbolication.data.SymbolMapRepositoryImpl
import io.github.youndie.katcher.module
import io.github.youndie.katcher.retrace.MappingFileStorageOkio
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-04: a report's `trace.id` leads to the trace — as a link on the page, as a scalar over MCP. */
class TraceLinkTest : RepositoryTest() {
    private val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"
    private val template = "https://tracy.example/traces/{traceId}"

    /** Stores one crash carrying [trace] as its `trace.id`, through ingest; answers the report page's path. */
    private suspend fun crashWith(trace: String): String {
        val appId = AppRepositoryImpl(db, AppsCrudRepositoryImpl).create("api", AppType.JVM).id
        val reports = ReportRepositoryImpl(db)
        ProcessReportUseCase(
            symbolicationService =
                SymbolicationService(
                    symbolMapRepository = SymbolMapRepositoryImpl(db, SymbolMapCrudRepositoryImpl),
                    fileStorage = MappingFileStorageOkio,
                    strategies = emptyMap(),
                ),
            errorGroupRepository = ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl),
            reportRepository = reports,
            visitedRepository = ErrorGroupViewedRepositoryImpl(db),
        ).process(
            CreateReportParams(
                appKey = "unused-here",
                message = "boom",
                stacktrace = "java.lang.IllegalStateException: boom\n\tat com.example.api.Orders.place(Orders.kt:12)",
                context = mapOf(TRACE_ID_KEY to trace),
            ),
            appId,
        )
        val report = reports.findByApp(appId, 1, 1).items.single()
        return "/apps/$appId/errors/1/reports/${report.id}"
    }

    private fun ApplicationTestBuilder.katcher(traceUrl: String?) {
        application { module(db, ServerConfig(mcpToken = TOKEN, traceUrlTemplate = traceUrl), KatcherProbes(db)) }
    }

    private suspend fun ApplicationTestBuilder.page(path: String): String =
        client
            .get(path) {
                header(X_AUTH_REQUEST_USER, "tester")
                header(X_AUTH_REQUEST_EMAIL, "tester@example.test")
            }.bodyAsText()

    /** The scalars `get_crash_metadata` answers for group 1; arrays and objects come back as null. */
    private suspend fun ApplicationTestBuilder.metadata(): Map<String, String?> {
        val body =
            client
                .post("/mcp") {
                    header(HttpHeaders.Authorization, "Bearer $TOKEN")
                    header(HttpHeaders.Accept, "application/json, text/event-stream")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_crash_metadata",""" +
                            """"arguments":{"groupId":1}}}""",
                    )
                }.bodyAsText()
        val text =
            Json
                .parseToJsonElement(body)
                .jsonObject
                .getValue("result")
                .jsonObject
                .getValue("content")
                .jsonArray
                .first()
                .jsonObject
                .getValue("text")
                .jsonPrimitive.content
        return Json.parseToJsonElement(text).jsonObject.mapValues { (_, value) -> (value as? JsonPrimitive)?.content }
    }

    @Test
    fun `the report page links the trace id to the configured tracer`() =
        testApplication {
            setupSchema()
            val path = crashWith(traceId)
            katcher(template)

            val html = page(path)

            assertTrue(
                "href=\"https://tracy.example/traces/$traceId\"" in html,
                "no link to the trace on the report page",
            )
        }

    @Test
    fun `without a template the trace id is text and not a link`() =
        testApplication {
            setupSchema()
            val path = crashWith(traceId)
            katcher(traceUrl = null)

            val html = page(path)

            assertTrue(traceId in html, "the trace id is not on the page at all")
            assertFalse("href=\"https://tracy.example" in html)
        }

    @Test
    fun `get_crash_metadata returns a trace id of the W3C shape`() =
        testApplication {
            setupSchema()
            crashWith(traceId)
            katcher(template)

            assertEquals(traceId, metadata()["traceId"])
        }

    @Test
    fun `get_crash_metadata omits a trace id that carries markup`() =
        testApplication {
            setupSchema()
            crashWith("$traceId\"><b>")
            katcher(template)

            val metadata = metadata()

            assertTrue("contextKeys" in metadata, "the metadata answer itself did not arrive: $metadata")
            assertFalse("traceId" in metadata, "an id carrying markup reached the agent: $metadata")
        }

    private companion object {
        const val TOKEN = "FAKE-test-secret"
    }
}
