package ru.workinprogress.katcher.mcp

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import kotlinx.io.readString
import ru.workinprogress.katcher.ServerConfig

const val MCP_PATH = "/mcp"

private const val BEARER_PREFIX = "Bearer "

/**
 * Installs the MCP endpoint, but only when a token is configured.
 *
 * An unset token means the feature is off, not open: neither the guard nor the transport
 * is installed, so there is nothing to reach even if the proxy in front is misconfigured.
 *
 * A static bearer token is the pragmatic choice for a single-tenant self-hosted
 * deployment. The MCP specification's full OAuth 2.1 flow (protected resource metadata,
 * resource indicators) is deliberately not implemented — a known gap, not an oversight.
 */
fun Application.installMcp(
    config: ServerConfig,
    mcpServer: KatcherMcpServer,
) {
    val token = config.mcpToken
    if (token == null) {
        log.info("MCP endpoint disabled: MCP_TOKEN is not set")
        return
    }

    // Guarded by a pipeline interceptor rather than the Authentication plugin because the
    // SDK's transport installs its own routing on the Application and cannot be nested
    // inside an `authenticate {}` block. Registered before the transport so it runs first.
    //
    // Notably this does NOT reuse the header-trusting provider the HTML pages use: that
    // one accepts whatever identity the caller claims, which is only safe behind the proxy
    // and would hand an attacker any user they asked for on a machine-facing endpoint.
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path().startsWith(MCP_PATH) && !call.hasValidBearer(token)) {
            this@installMcp.log.warn("Rejected unauthenticated MCP request")
            call.respond(HttpStatusCode.Unauthorized)
            finish()
        }
    }

    // Clamps the protocol version an initialize request asks for, working around an SDK
    // serialisation bug that otherwise drops protocolVersion from the response and stops
    // any up-to-date client connecting. See McpProtocolCompat.
    install(
        createApplicationPlugin("McpProtocolCompat") {
            onCallReceive { call, _ ->
                if (call.request.path().startsWith(MCP_PATH)) {
                    transformBody { body: ByteReadChannel ->
                        val raw = body.readRemaining().readString()
                        ByteReadChannel(McpProtocolCompat.normalizeRequest(raw))
                    }
                }
            }
        },
    )

    // Stateless: these tools are read-only lookups with no session to resume, so the
    // simpler transport avoids carrying session and event-store machinery for nothing.
    //
    // DNS rebinding protection stays on — it stops a page on another origin from driving
    // this endpoint through the developer's own machine. It validates the Host header
    // against allowedHosts, which defaults to localhost only; a deployment therefore has
    // to declare its own hostname or every request is refused with "Invalid Host". Left
    // null when unset so local runs keep the safe default.
    val allowedHosts = config.mcpAllowedHosts.takeIf { it.isNotEmpty() }
    mcpStatelessStreamableHttp(path = MCP_PATH, allowedHosts = allowedHosts) {
        mcpServer.build()
    }
    log.info("MCP allowed hosts: ${allowedHosts?.joinToString() ?: "default (localhost)"}")

    log.info("MCP endpoint enabled at $MCP_PATH")
}

private fun ApplicationCall.hasValidBearer(expected: String): Boolean {
    val header = request.headers["Authorization"] ?: return false
    if (!header.startsWith(BEARER_PREFIX)) return false
    return constantTimeEquals(header.removePrefix(BEARER_PREFIX), expected)
}

/**
 * Compares without an early exit, so response timing does not reveal how many leading
 * characters of a guessed token were correct.
 */
private fun constantTimeEquals(
    a: String,
    b: String,
): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
}
