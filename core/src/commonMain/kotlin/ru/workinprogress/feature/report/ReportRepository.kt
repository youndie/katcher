package ru.workinprogress.feature.report

interface ReportRepository {
    suspend fun insert(
        appId: Int,
        groupId: Long,
        report: CreateReportParams,
    ): Report

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
}
