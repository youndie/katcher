package ru.workinprogress.katcher

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun getServerConfig(): ServerConfig =
    runCatching {
        getenv(DB_PATH)?.toKString() ?: "./data/local.db"
    }.getOrNull()?.let {
        ServerConfig(it)
    } ?: ServerConfig()
