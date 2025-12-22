package ru.workinprogress.katcher

const val DB_PATH = "DB_PATH"
const val SOURCE_MAPS_PATH = "SOURCE_MAPS_PATH"

expect fun getServerConfig(): ServerConfig
