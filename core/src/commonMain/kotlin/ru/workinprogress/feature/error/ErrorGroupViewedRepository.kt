package ru.workinprogress.feature.error

interface ErrorGroupViewedRepository {
    suspend fun updateVisitedAt(
        errorGroupId: Long,
        forUserId: Int,
    )

    suspend fun removeVisits(errorGroupId: Long)
}
