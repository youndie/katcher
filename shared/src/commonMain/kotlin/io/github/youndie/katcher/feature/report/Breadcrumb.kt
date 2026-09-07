package io.github.youndie.katcher.feature.report

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
public data class Breadcrumb(
    val timestamp: LocalDateTime,
    val type: String,
    val message: String,
    val data: Map<String, String>? = null,
)
