package ru.workinprogress.feature.report.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.Report
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.ReportsPaginated
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ReportRepositoryImpl : ReportRepository {
    override suspend fun insert(
        appId: Int,
        groupId: Long,
        report: CreateReportParams,
    ): Report =
        withContext(Dispatchers.IO) {
            transaction {
                val id =
                    Reports.insertAndGetId {
                        it[Reports.appId] = appId
                        it[Reports.groupId] = groupId
                        it[message] = report.message
                        it[stacktrace] = report.stacktrace
                        it[timestamp] = Clock.System.now().toEpochMilliseconds()
                        it[context] = Json.encodeToString(report.context)
                        it[release] = report.release
                        it[environment] = report.environment
                    }

                Reports
                    .selectAll()
                    .where { Reports.id eq id }
                    .single()
                    .let { rowToReport(it) }
            }
        }

    override suspend fun findByApp(
        appId: Int,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated =
        withContext(Dispatchers.IO) {
            transaction {
                val total = Reports.selectAll().where { Reports.appId eq appId }.count()

                val list =
                    Reports
                        .selectAll()
                        .where { Reports.appId eq appId }
                        .limit(pageSize)
                        .offset((page - 1) * pageSize.toLong())
                        .map { rowToReport(it) }

                ReportsPaginated(
                    list,
                    page = page,
                    totalPages = ((total + pageSize - 1) / pageSize).toInt(),
                )
            }
        }

    override suspend fun findByGroup(
        groupId: Long,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated =
        withContext(Dispatchers.IO) {
            transaction {
                val total = Reports.selectAll().where { Reports.groupId eq groupId }.count()

                val list =
                    Reports
                        .selectAll()
                        .where { Reports.groupId eq groupId }
                        .orderBy(Reports.timestamp, SortOrder.DESC)
                        .limit(pageSize)
                        .offset((page - 1) * pageSize.toLong())
                        .map { rowToReport(it) }

                ReportsPaginated(
                    items = list,
                    page = page,
                    totalPages = ((total + pageSize - 1) / pageSize).toInt(),
                )
            }
        }

    private fun rowToReport(row: ResultRow): Report =
        Report(
            row[Reports.id].value,
            row[Reports.message],
            row[Reports.stacktrace],
            Instant.fromEpochMilliseconds(row[Reports.timestamp]).toLocalDateTime(TimeZone.currentSystemDefault()),
            row[Reports.context]?.let { Json.decodeFromString(it) },
            row[Reports.release],
            row[Reports.environment],
        )
}
