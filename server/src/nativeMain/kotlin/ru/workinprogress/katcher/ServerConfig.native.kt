@file:OptIn(ExperimentalForeignApi::class)

package ru.workinprogress.katcher

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun getServerConfig(): ServerConfig =
    ServerConfig(
        getDBPath ?: "./data/local.db",
        getSourceMapsPath ?: "./data/mappings",
        getMcpToken,
    )

val getMcpToken
    get() =
        runCatching {
            getenv(MCP_TOKEN)?.toKString()?.takeIf { it.isNotBlank() }
        }.getOrNull()

val getDBPath
    get() =
        runCatching {
            getenv(DB_PATH)?.toKString() ?: "./data/local.db"
        }.getOrNull()

val getSourceMapsPath
    get() =
        runCatching {
            getenv(SOURCE_MAPS_PATH)?.toKString() ?: "./data/mappings"
        }.getOrNull()
