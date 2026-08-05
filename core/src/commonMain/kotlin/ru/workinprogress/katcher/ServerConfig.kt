package ru.workinprogress.katcher

class ServerConfig(
    val sqlitePath: String = "./data/local.db",
    val sourceMapPath: String = "./data/mappings",
    /** When null, the MCP endpoint is not exposed at all. */
    val mcpToken: String? = null,
    /**
     * Hostnames the MCP endpoint accepts in the Host header. Empty keeps the SDK default
     * of localhost only, so a deployment must declare its public hostname explicitly.
     */
    val mcpAllowedHosts: List<String> = emptyList(),
)
