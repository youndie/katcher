package io.github.youndie.katcher

const val DB_PATH = "DB_PATH"
const val SOURCE_MAPS_PATH = "SOURCE_MAPS_PATH"

/**
 * Token for the MCP endpoint, presented as `Authorization: Bearer <token>` — the scheme is
 * required. The MCP route is not mounted at all unless this is set, so the surface stays
 * closed by default rather than relying on the reverse proxy — unlike the HTML pages, an MCP
 * client is a machine and never carries a browser session.
 */
const val MCP_TOKEN = "MCP_TOKEN"

/**
 * Comma-separated hostnames (`host` or `host:port`) the MCP endpoint may be reached on; any
 * other Host is refused with 400. Unset or empty means the Host header is NOT checked — not
 * "localhost only" — so a deployment sets it. An entry that is not a host name stops the server
 * at start rather than refusing every request.
 */
const val MCP_ALLOWED_HOSTS = "MCP_ALLOWED_HOSTS"

/**
 * Where the metrik agent sends its packets, as `host:port`. Unset means no monitoring at all:
 * the plugin is not installed, and nothing is measured or sent.
 */
const val METRIK_ENDPOINT = "METRIK_ENDPOINT"

/** Ingest key of the metrik installation. One per installation, not per service. */
const val METRIK_KEY = "METRIK_KEY"

/** Name katcher reports under. Defaults to `katcher`. */
const val METRIK_SERVICE = "METRIK_SERVICE"

/** Release, so metrik can draw deploy markers on the charts. Optional. */
const val METRIK_RELEASE = "METRIK_RELEASE"

expect fun getServerConfig(): ServerConfig

/**
 * Ends the process with [code].
 *
 * `expect` for something both targets have: `kotlin.system.exitProcess` is declared for the JVM and
 * for Kotlin/Native, not in common, so `commonMain` cannot see it.
 */
expect fun endProcess(code: Int): Nothing
