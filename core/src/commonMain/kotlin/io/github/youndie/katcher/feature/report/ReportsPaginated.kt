package io.github.youndie.katcher.feature.report

import kotlinx.serialization.Serializable

@Serializable
data class ReportsPaginated(
    val items: List<Report>,
    val page: Int,
    val totalPages: Int,
)
