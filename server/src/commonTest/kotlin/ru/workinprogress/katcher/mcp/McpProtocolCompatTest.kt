package ru.workinprogress.katcher.mcp

import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Guards the workaround for the SDK dropping `protocolVersion` from InitializeResult
 * whenever the negotiated value equals the field's default. Without it no current client
 * can connect at all, so a regression here is not subtle: it takes the server offline for
 * every up-to-date agent.
 */
class McpProtocolCompatTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun initialize(version: String) =
        """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
          "protocolVersion":"$version","capabilities":{},
          "clientInfo":{"name":"probe","version":"1"}}}
        """.trimIndent()

    private fun versionOf(body: String) =
        json
            .parseToJsonElement(body)
            .jsonObject["params"]
            ?.jsonObject
            ?.get("protocolVersion")
            ?.jsonPrimitive
            ?.content

    @Test
    fun `a version newer than the SDK knows is clamped to one that serialises`() {
        // The real case: Claude Code asks for a version the SDK has never heard of.
        val out = McpProtocolCompat.normalizeRequest(initialize("2026-07-28"))
        val version = versionOf(out)
        assertNotEquals(LATEST_PROTOCOL_VERSION, version, "must not negotiate to the field's default")
        assertNotEquals("2026-07-28", version)
    }

    @Test
    fun `the SDK's own latest version is clamped too`() {
        // Supported, but equal to the default — so it hits exactly the same bug.
        val version = versionOf(McpProtocolCompat.normalizeRequest(initialize(LATEST_PROTOCOL_VERSION)))
        assertNotEquals(LATEST_PROTOCOL_VERSION, version)
    }

    @Test
    fun `older supported versions are left alone`() {
        // These already serialise correctly; rewriting them would downgrade for no reason.
        listOf("2024-11-05", "2025-03-26", "2025-06-18").forEach { v ->
            assertEquals(v, versionOf(McpProtocolCompat.normalizeRequest(initialize(v))), "changed $v")
        }
    }

    @Test
    fun `other methods pass through untouched`() {
        val body = """{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""
        assertEquals(body, McpProtocolCompat.normalizeRequest(body))
    }

    @Test
    fun `initialize without a protocol version is untouched`() {
        val body = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"capabilities":{}}}"""
        assertEquals(body, McpProtocolCompat.normalizeRequest(body))
    }

    @Test
    fun `malformed input passes through rather than throwing`() {
        // A shim must never be the thing that breaks a request; rejecting bad input is the
        // SDK's job.
        listOf("", "not json", "[1,2,3]", """{"method":123}""").forEach { body ->
            assertEquals(body, McpProtocolCompat.normalizeRequest(body), "changed $body")
        }
    }

    @Test
    fun `the rest of the request survives rewriting`() {
        val out = McpProtocolCompat.normalizeRequest(initialize("2026-07-28"))
        val root = json.parseToJsonElement(out).jsonObject
        assertEquals("2.0", root["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("initialize", root["method"]?.jsonPrimitive?.content)
        assertEquals("1", root["id"]?.jsonPrimitive?.content)
        val params = root["params"]!!.jsonObject
        assertTrue("capabilities" in params)
        assertEquals(
            "probe",
            params["clientInfo"]
                ?.jsonObject
                ?.get("name")
                ?.jsonPrimitive
                ?.content,
        )
    }
}
