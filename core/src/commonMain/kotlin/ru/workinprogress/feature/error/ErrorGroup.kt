package ru.workinprogress.feature.error

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ru.workinprogress.feature.report.ErrorGroupFilter
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder

@Serializable
data class ErrorGroupsPaginated(
    val items: List<ErrorGroupWithViewed>,
    val page: Int,
    val totalPages: Int,
    val sortBy: ErrorGroupSort,
    val sortOrder: ErrorGroupSortOrder,
    /** Groups the filters let through, and groups the app has at all — "12 of 47". */
    val total: Int = 0,
    val totalUnfiltered: Int = 0,
    val filter: ErrorGroupFilter = ErrorGroupFilter(),
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
    /** Pull request an agent reported as fixing this group, if any. */
    val fixUrl: String? = null,
    /**
     * Composed title. Null on groups created before it existed — those fall back to [title],
     * which is the truncated head of the stacktrace.
     */
    val exceptionType: String? = null,
    val message: String? = null,
    val location: String? = null,
    /** When a report arrived after somebody had marked this group resolved. */
    val regressedAt: Long? = null,
    val regressedRelease: String? = null,
) {
    val summary: CrashSummary get() = CrashSummary(exceptionType, message, location)

    val regressed: Boolean get() = regressedAt != null && !resolved
}

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
    val exceptionType: String? = null,
    val message: String? = null,
    val location: String? = null,
)
