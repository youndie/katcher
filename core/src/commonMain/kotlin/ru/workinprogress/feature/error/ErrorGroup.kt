package ru.workinprogress.feature.error

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder

@Serializable
data class ErrorGroupsPaginated(
    val items: List<ErrorGroupWithViewed>,
    val page: Int,
    val totalPages: Int,
    val sortBy: ErrorGroupSort,
    val sortOrder: ErrorGroupSortOrder,
)

@Serializable
data class ErrorGroup(
    val id: Long,
    val appId: Int,
    val fingerprint: String,
    val title: String,
    val firstSeen: LocalDateTime,
    val lastSeen: LocalDateTime,
    val occurrences: Int,
    val resolved: Boolean,
)

@Serializable
data class ErrorGroupWithViewed(
    val errorGroup: ErrorGroup,
    val viewed: Boolean,
)

@Serializable
data class CreateErrorGroupParams(
    val appId: Int,
    val fingerprint: String,
    val title: String,
)
