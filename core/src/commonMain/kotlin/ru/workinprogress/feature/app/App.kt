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

/** What the badge on a card says — the enum name is for the wire, not for a reader. */
val AppType.label: String
    get() =
        when (this) {
            AppType.JVM -> "JVM"
            AppType.COMPOSE_MULTIPLATFORM -> "Compose MP"
            AppType.ANDROID -> "Android"
            AppType.OTHER -> "Other"
        }
