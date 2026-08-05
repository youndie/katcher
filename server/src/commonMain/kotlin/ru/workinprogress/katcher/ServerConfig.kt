package ru.workinprogress.katcher

const val DB_PATH = "DB_PATH"
const val SOURCE_MAPS_PATH = "SOURCE_MAPS_PATH"

/**
 * Bearer token for the MCP endpoint. The MCP route is not mounted at all unless this is
 * set, so the surface stays closed by default rather than relying on the reverse proxy —
 * unlike the HTML pages, an MCP client is a machine and never carries a browser session.
 */
const val MCP_TOKEN = "MCP_TOKEN"

expect fun getServerConfig(): ServerConfig
