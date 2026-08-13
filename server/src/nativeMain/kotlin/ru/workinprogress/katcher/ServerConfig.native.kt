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
        getMcpAllowedHosts,
        metrikEndpoint = env(METRIK_ENDPOINT),
        metrikKey = env(METRIK_KEY),
        metrikService = env(METRIK_SERVICE) ?: "katcher",
        metrikRelease = env(METRIK_RELEASE),
    )

private fun env(name: String): String? =
    runCatching {
        getenv(name)?.toKString()?.takeIf { it.isNotBlank() }
    }.getOrNull()

val getMcpAllowedHosts: List<String>
    get() =
        runCatching {
            getenv(MCP_ALLOWED_HOSTS)?.toKString().orEmpty()
        }.getOrNull()
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

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
