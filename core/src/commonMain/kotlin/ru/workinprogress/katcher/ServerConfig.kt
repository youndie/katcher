package ru.workinprogress.katcher

class ServerConfig(
    val sqlitePath: String = "./data/local.db",
    val sourceMapPath: String = "./data/mappings",
    /** When null, the MCP endpoint is not exposed at all. */
    val mcpToken: String? = null,
)
