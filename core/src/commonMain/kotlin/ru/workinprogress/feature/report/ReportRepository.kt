package ru.workinprogress.feature.report

interface ReportRepository {
    suspend fun insert(
        appId: Int,
        groupId: Long,
        report: CreateReportParams,
    )

    suspend fun findByApp(
        appId: Int,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated

    suspend fun findByGroup(
        groupId: Long,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated

    suspend fun getReportById(reportId: Long): Report?

    /**
     * What the rows of the group list need beyond the group itself: the shape of the last
     * days, and which environment and releases the reports came from. One call for the whole
     * visible page — a query per row would be fifteen.
     */
    suspend fun activity(
        groupIds: List<Long>,
        now: Long,
        days: Int,
    ): Map<Long, GroupActivity>

    /** How the reports of one group split across releases, most reports first. */
    suspend fun releases(
        groupId: Long,
        limit: Int,
    ): List<ReleaseCount>
}

data class ReleaseCount(
    val release: String,
    val count: Int,
)

/**
 * [releases] is a range rather than a list: a row has space for "1.4.1 – 1.4.2" and none for
 * eleven versions. [environment] is null when reports came from more than one — saying
 * "production" of a group that also crashes in staging would be a lie the row cannot qualify.
 */
data class GroupActivity(
    val groupId: Long,
    val dailyCrashes: List<Int>,
    val environment: String?,
    val firstRelease: String?,
    val lastRelease: String?,
) {
    val releases: String?
        get() =
            when {
                firstRelease == null -> lastRelease
                lastRelease == null || firstRelease == lastRelease -> firstRelease
                else -> "$firstRelease – $lastRelease"
            }
}
