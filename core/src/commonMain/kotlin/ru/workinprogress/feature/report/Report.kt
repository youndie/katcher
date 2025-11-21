package ru.workinprogress.feature.report

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Report(
    val id: Long,
    val message: String,
    val stacktrace: String,
    val timestamp: LocalDateTime,
    val context: Map<String, String>? = null,
    val release: String? = null,
    val environment: String? = null,
)
