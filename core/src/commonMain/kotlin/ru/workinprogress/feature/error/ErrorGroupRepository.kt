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

    /**
     * A report arrived for a group somebody had already marked resolved. The flag is cleared
     * — a bug that is back is not fixed — and the release it came back in is recorded, so the
     * row can say which one.
     */
    suspend fun markRegressed(
        groupId: Long,
        release: String?,
        at: Long,
    )

    /** Records the pull request an agent reported as fixing this group. */
    suspend fun linkFix(
        groupId: Long,
        fixUrl: String,
        linkedAt: Long,
    )
}
