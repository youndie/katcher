package ru.workinprogress.feature.report

import kotlinx.serialization.Serializable

@Serializable
data class CreateReportParams(
    val appKey: String,
    val message: String,
    val stacktrace: String,
    val context: Map<String, String>? = null,
    val release: String? = null,
    val environment: String? = null,
)
