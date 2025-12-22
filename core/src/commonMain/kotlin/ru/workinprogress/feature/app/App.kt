package ru.workinprogress.feature.app

import kotlinx.serialization.Serializable

@Serializable
data class App(
    val id: Int,
    val name: String,
    val type: AppType,
    val apiKey: String,
)

enum class AppType {
    JVM,
    COMPOSE_MULTIPLATFORM,
    ANDROID,
    OTHER,
}
