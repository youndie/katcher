package ru.workinprogress.feature.report

import kotlinx.serialization.Serializable

@Serializable
data class ReportsPaginated(
    val items: List<Report>,
    val page: Int,
    val totalPages: Int,
)
