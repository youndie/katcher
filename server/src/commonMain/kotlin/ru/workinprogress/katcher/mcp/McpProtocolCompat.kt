package ru.workinprogress.katcher.mcp

import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Works around an SDK bug that makes the server unusable by any client asking for a
 * protocol version it does not know.
 *
 * `InitializeResult.protocolVersion` is declared with a default of
 * [LATEST_PROTOCOL_VERSION], and kotlinx.serialization omits values equal to their
 * default. So whenever the negotiated version happens to be the latest one — which is
 * exactly what the SDK falls back to for anything it does not recognise — the field is
 * dropped from the response entirely. The client then fails schema validation with
 * "protocolVersion: expected string, received undefined" and the connection never opens.
 *
 * Confirmed against SDK 0.15.0: 2025-06-18 is echoed back, while 2025-11-25 (the SDK's own
 * latest) and anything newer come back with no protocolVersion at all. The negotiation
 * logic itself is correct; only the serialisation loses the value.
 *
 * The fix rewrites the *request* rather than patching the response: an initialize asking
 * for a version that would negotiate to the latest is rewritten to the newest supported
 * version that is not the default, so the SDK's own handler produces a value that does get
 * serialised. Nothing downstream changes behaviour — the SDK still runs its normal
 * negotiation, just on an input that dodges the bug.
 *
 * Remove this once the SDK annotates the field with `@EncodeDefault` or drops the default.
 */
object McpProtocolCompat {
    private const val INITIALIZE = "initialize"

    /**
     * Newest supported version whose serialisation survives, i.e. any supported version
     * other than the one used as the field's default.
     */
    private val SAFE_VERSION: String =
        SUPPORTED_PROTOCOL_VERSIONS.firstOrNull { it != LATEST_PROTOCOL_VERSION }
            ?: LATEST_PROTOCOL_VERSION

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the body to hand to the SDK: either [body] unchanged, or an initialize
     * request with its protocolVersion clamped to [SAFE_VERSION].
     *
     * Anything unparseable is passed through untouched — this is a compatibility shim, not
     * a validator, and the SDK is responsible for rejecting malformed input.
     */
    fun normalizeRequest(body: String): String {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return body
        if (root["method"]?.jsonPrimitive?.contentOrNullSafe() != INITIALIZE) return body

        val params = root["params"] as? JsonObject ?: return body
        val requested = params["protocolVersion"]?.jsonPrimitive?.contentOrNullSafe() ?: return body

        // Only versions that would end up as the default are a problem. A client asking for
        // an older supported version already works and is left alone.
        val negotiated = if (requested in SUPPORTED_PROTOCOL_VERSIONS) requested else LATEST_PROTOCOL_VERSION
        if (negotiated != LATEST_PROTOCOL_VERSION) return body

        val patchedParams =
            buildJsonObject {
                params.forEach { (key, value) -> put(key, value) }
                put("protocolVersion", SAFE_VERSION)
            }
        val patched =
            buildJsonObject {
                root.forEach { (key, value) -> if (key != "params") put(key, value) }
                put("params", patchedParams)
            }
        return patched.toString()
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()
}
