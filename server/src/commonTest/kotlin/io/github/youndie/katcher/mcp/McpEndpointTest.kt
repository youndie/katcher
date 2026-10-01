package io.github.youndie.katcher.mcp

import io.github.youndie.katcher.KatcherProbes
import io.github.youndie.katcher.ServerConfig
import io.github.youndie.katcher.data.RepositoryTest
import io.github.youndie.katcher.module
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The MCP endpoint as a client meets it: katcher's whole module, through Ktor's test engine.
 *
 * What the endpoint does with a token, a `Host` header or a protocol version is kore-mcp's, and kore
 * tests it. What is katcher's is the wiring — that the module installs the endpoint behind its own
 * ContentNegotiation and registers the five tools on it — and the one property katcher's own copy of
 * that wiring got wrong: which requests the token check sees.
 *
 * A file database, for the reason [RepositoryTest] gives; nothing here reads it.
 */
class McpEndpointTest : RepositoryTest() {
    // Configured as the chart configures it: the token, and `MCP_ALLOWED_HOSTS` filled from `hostname`.
    private fun ApplicationTestBuilder.katcher() {
        application { module(db, ServerConfig(mcpToken = TOKEN, mcpAllowedHosts = listOf(HOST)), KatcherProbes(db)) }
    }

    private suspend fun ApplicationTestBuilder.rpc(
        body: String,
        path: String = "/mcp",
        token: String? = TOKEN,
    ): HttpResponse =
        client.post {
            // Set encoded: a path given decoded is normalised by the client, and the request would
            // reach the server as plain `/mcp` whatever the test asked for.
            url { encodedPath = path }
            // Every real client sends one; the test engine sends none unless asked.
            header(HttpHeaders.Host, HOST)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun initialize(version: String = "2025-06-18") =
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$version",""" +
            """"capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""

    private suspend fun HttpResponse.result(): JsonObject =
        Json
            .parseToJsonElement(bodyAsText())
            .jsonObject
            .getValue("result")
            .jsonObject

    @Test
    fun `the tool list should carry the five tools with link_fix the only writer`() =
        testApplication {
            // Given
            katcher()

            // When
            val response = rpc("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val readOnly =
                response.result().getValue("tools").jsonArray.associate { tool ->
                    val annotations = tool.jsonObject.getValue("annotations").jsonObject
                    tool.jsonObject
                        .getValue("name")
                        .jsonPrimitive.content to
                        annotations.getValue("readOnlyHint").jsonPrimitive.boolean
                }
            assertEquals(
                mapOf(
                    "list_apps" to true,
                    "list_error_groups" to true,
                    "get_crash_metadata" to true,
                    "get_crash_content" to true,
                    "link_fix" to false,
                ),
                readOnly,
            )
        }

    @Test
    fun `an initialize asking for the latest protocol should get its version back`() =
        testApplication {
            // Given — katcher's ContentNegotiation omits defaults, and SDK 0.15.0 answers through it:
            // without kore-mcp encoding the response itself, this is the field that goes missing and
            // no current client connects.
            katcher()

            // When
            val response = rpc(initialize(LATEST_PROTOCOL_VERSION))

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(LATEST_PROTOCOL_VERSION, response.result()["protocolVersion"]?.jsonPrimitive?.content)
        }

    /**
     * Ktor's router drops empty path segments and decodes each one, so every one of these reaches the
     * handler `/mcp` reaches, while the request's path string is something else. A check that decides
     * from that string whether it applies does not apply to them — the token check has to sit where
     * routing puts the request.
     */
    private val foldedPaths = listOf("//mcp", "///mcp", "/%6Dcp", "/%6dcp")

    @Test
    fun `a path the router folds into the endpoint should still need the token`() =
        testApplication {
            // Given
            katcher()

            (listOf("/mcp") + foldedPaths).forEach { path ->
                // When
                val response = rpc(initialize(), path = path, token = null)

                // Then
                assertEquals(HttpStatusCode.Unauthorized, response.status, path)
            }
        }

    @Test
    fun `the folded paths should reach the transport with a valid token`() =
        testApplication {
            // Given — the control for the test above: without it, its 401 could mean the path never
            // reached the router as written rather than that the check recognised it.
            katcher()

            foldedPaths.forEach { path ->
                // When
                val response = rpc(initialize(), path = path)

                // Then
                assertEquals(HttpStatusCode.OK, response.status, path)
            }
        }

    private companion object {
        const val TOKEN = "secret"
        const val HOST = "katcher.example.com"
    }
}
