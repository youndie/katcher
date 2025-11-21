package ru.workinprogress.feature.error

import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder

interface ErrorGroupRepository {
    suspend fun findByFingerprint(
        appId: Int,
        fingerprint: String,
    ): ErrorGroup?

    suspend fun updateOccurrences(id: Long)

    suspend fun insert(newGroup: CreateErrorGroupParams): ErrorGroup

    suspend fun findByAppId(
        appId: Int,
        userId: Int,
        page: Int,
        pageSize: Int,
        sortBy: ErrorGroupSort,
        sortOrder: ErrorGroupSortOrder,
    ): ErrorGroupsPaginated

    suspend fun findById(groupId: Long): ErrorGroup?

    suspend fun resolve(groupId: Long)
}
