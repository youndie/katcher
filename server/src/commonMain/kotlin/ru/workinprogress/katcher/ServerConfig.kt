package ru.workinprogress.katcher

const val DB_PATH = "DB_PATH"
const val SOURCE_MAPS_PATH = "SOURCE_MAPS_PATH"

/**
 * Bearer token for the MCP endpoint. The MCP route is not mounted at all unless this is
 * set, so the surface stays closed by default rather than relying on the reverse proxy —
 * unlike the HTML pages, an MCP client is a machine and never carries a browser session.
 */
const val MCP_TOKEN = "MCP_TOKEN"

/**
 * Comma-separated hostnames the MCP endpoint may be reached on. Required in a deployment:
 * the transport's DNS-rebinding protection otherwise accepts localhost only.
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
