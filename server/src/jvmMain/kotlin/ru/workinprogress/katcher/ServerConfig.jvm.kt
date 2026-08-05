package ru.workinprogress.katcher

actual fun getServerConfig(): ServerConfig =
    ServerConfig(
        sqlitePath = runCatching { System.getenv(DB_PATH) }.getOrNull() ?: "./data/local.db",
        mcpToken = runCatching { System.getenv(MCP_TOKEN) }.getOrNull()?.takeIf { it.isNotBlank() },
        mcpAllowedHosts =
            runCatching { System.getenv(MCP_ALLOWED_HOSTS) }
                .getOrNull()
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
    )
