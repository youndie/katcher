package ru.workinprogress.katcher

actual fun getServerConfig(): ServerConfig = ServerConfig(sqlitePath = runCatching { System.getenv(DB_PATH) }.getOrNull() ?: "local.db")
