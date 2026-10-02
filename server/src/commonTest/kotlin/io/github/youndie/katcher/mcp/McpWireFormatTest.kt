package io.github.youndie.katcher.mcp

import io.github.youndie.katcher.KatcherProbes
import io.github.youndie.katcher.ServerConfig
import io.github.youndie.katcher.common
import io.github.youndie.katcher.data.RepositoryTest
import io.github.youndie.katcher.initDi
import io.github.youndie.katcher.module
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The warning katcher logs at start when `MCP_TOKEN` is set — `ContentNegotiation is already
 * installed. MCP requires json(McpJson)…` — and the question behind it: do MCP's messages leave in
 * katcher's JSON instead of the protocol's? SDK 0.15.0 prints it whenever the application already has
 * ContentNegotiation, because Ktor's public API does not show which `Json` that is; it cannot look.
 *
 * For katcher the warning is not idle. Its `Json` (`common()`) pretty-prints and does not write
 * defaults. One thing on the SDK's stateless transport goes through the application's
 * ContentNegotiation — the answer to a `POST` in JSON mode — and under that `Json` an `initialize`
 * answer loses `protocolVersion` (no current client connects) and a `ping` answer loses its whole
 * `result` (no longer JSON-RPC). kore-mcp encodes that answer with `McpJson` on the transport's route
 * before ContentNegotiation sees it; this test is what holds katcher's module to that.
 *
 * The measure is [mismatch], on the raw body: every answer must be exactly what `McpJson` writes for
 * the message inside it. A different `Json` breaks the equality — a missing default, an extra `null`,
 * whitespace.
 */
class McpWireFormatTest : RepositoryTest() {
    private fun initialize(version: String) =
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$version",""" +
            """"capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""

    /**
     * Requests the transport answers with a message: `initialize` with the latest protocol version
     * (which is also the field's default) and with an older one, `ping` (whose whole `result` is a
     * default), the tool list, a call without the optional fields, a call that ends in a tool error,
     * a tool and a method that do not exist, a batch of two, and a body that does not parse at all.
     */
    private val exchanges =
        listOf(
            Exchange("initialize latest", initialize(LATEST_PROTOCOL_VERSION)),
            Exchange("initialize older", initialize("2025-06-18")),
            Exchange("ping", """{"jsonrpc":"2.0","id":3,"method":"ping"}"""),
            Exchange("tools/list", """{"jsonrpc":"2.0","id":4,"method":"tools/list"}"""),
            Exchange(
                "tools/call without arguments",
                """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"list_apps"}}""",
            ),
            Exchange(
                "tools/call ending in a tool error",
                """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"get_crash_metadata",""" +
                    """"arguments":{"groupId":999}}}""",
            ),
            Exchange(
                "tools/call of a tool that does not exist",
                """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"no_such_tool"}}""",
            ),
            Exchange("method not found", """{"jsonrpc":"2.0","id":8,"method":"no/such/method"}"""),
            Exchange(
                "batch",
                """[{"jsonrpc":"2.0","id":9,"method":"ping"},{"jsonrpc":"2.0","id":10,"method":"tools/list"}]""",
            ),
            Exchange("unparseable body", """{"jsonrpc":"2.0","id":""", HttpStatusCode.BadRequest),
        )

    @Test
    fun `every MCP answer of the assembled server should be exactly what McpJson writes`() =
        testApplication {
            // Given — the module as `main` starts it: katcher's ContentNegotiation from `common()`
            // first, then `installKoreMcp`, which is the order in which the SDK warns.
            setupSchema()
            application { module(db, CONFIG, KatcherProbes(db)) }

            // When
            val mismatches = mismatches()

            // Then
            assertEquals(emptyMap(), mismatches)
        }

    /**
     * POSITIVE CONTROL for the test above: katcher's ContentNegotiation, katcher's five tools and the
     * same requests, but the transport installed by the bare SDK instead of kore-mcp, so the answer
     * goes out through `common()`'s `Json`. Without it the green above could mean the measure catches
     * nothing.
     *
     * Measured on SDK 0.15.0: nine of the ten answers differ — all but the unparseable body, which the
     * SDK writes itself. With pretty-printing taken out of katcher's `Json` three still do: `initialize`
     * with the latest version, `ping` and the batch that carries one, none of which `McpJson` can even
     * read back. Under `McpJson` itself the same bare SDK differs on none.
     *
     * **If this turns red, the test above has stopped proving anything.** Either the SDK no longer
     * answers through the application's ContentNegotiation — the startup warning will have gone too;
     * drop this control and the README's paragraph on the warning — or `common()`'s `Json` now writes
     * what `McpJson` writes, and the control needs a `Json` that does not.
     */
    @Test
    fun `control - the bare SDK should answer through katcher's Json`() =
        testApplication {
            // Given
            setupSchema()
            application {
                common()
                initDi(db, CONFIG)
                val tools = KatcherMcpServer(dependencies.resolve(), dependencies.resolve(), dependencies.resolve())
                mcpStatelessStreamableHttp(path = "/mcp", enableDnsRebindingProtection = false) {
                    Server(
                        INFO,
                        ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
                    ).apply { tools.register(this) }
                }
            }

            // When
            val mismatches = mismatches()

            // Then
            assertTrue(mismatches.isNotEmpty(), "the bare SDK answered in McpJson under katcher's Json - see the KDoc")
        }

    /** Each exchange of [exchanges] whose body did not match `McpJson`, with how it did not. */
    private suspend fun ApplicationTestBuilder.mismatches(): Map<String, String> =
        exchanges
            .mapNotNull { exchange ->
                val response =
                    client.post("/mcp") {
                        // Every real client sends one, and the module checks it against the allowlist.
                        header(HttpHeaders.Host, HOST)
                        header(HttpHeaders.Authorization, "Bearer $TOKEN")
                        header(HttpHeaders.Accept, "application/json, text/event-stream")
                        contentType(ContentType.Application.Json)
                        setBody(exchange.request)
                    }
                val body = response.bodyAsText()
                assertEquals(exchange.status, response.status, "${exchange.name}: $body")
                mismatch(body)?.let { exchange.name to it }
            }.toMap()

    private class Exchange(
        val name: String,
        val request: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
    )

    private companion object {
        const val TOKEN = "secret"
        const val HOST = "katcher.example.com"

        // Configured as the chart configures it: the token, and `MCP_ALLOWED_HOSTS` filled from `hostname`.
        val CONFIG = ServerConfig(mcpToken = TOKEN, mcpAllowedHosts = listOf(HOST))
        val INFO = Implementation(name = "katcher", version = "0.1.0")

        val message = serializer<JSONRPCMessage>()
        val batch = ListSerializer(message)

        /**
         * `null` if [body] is exactly what `McpJson` writes for the message (or batch) inside it;
         * otherwise how it differs. Both the read and the write are `McpJson`'s: a missing field comes
         * back as its default, an extra `null` is dropped, no whitespace is written, and the bytes
         * part; a body that lost what tells the message type apart does not decode at all.
         */
        fun mismatch(body: String): String? {
            val rewritten =
                runCatching {
                    if (body.startsWith("[")) {
                        McpJson.encodeToString(batch, McpJson.decodeFromString(batch, body))
                    } else {
                        McpJson.encodeToString(message, McpJson.decodeFromString(message, body))
                    }
                }.getOrElse { return "not a JSON-RPC message: ${it.message}" }
            return if (rewritten == body) null else "McpJson writes $rewritten, got $body"
        }
    }
}
