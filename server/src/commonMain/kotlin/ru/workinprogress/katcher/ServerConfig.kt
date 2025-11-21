package ru.workinprogress.katcher

class ServerConfig(
    val sqlitePath: String = "./data/local.db",
)

const val DB_PATH = "DB_PATH"

expect fun getServerConfig(): ServerConfig
