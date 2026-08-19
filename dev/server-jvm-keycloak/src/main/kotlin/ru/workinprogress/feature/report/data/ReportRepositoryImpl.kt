package ru.workinprogress.feature.report.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.GroupActivity
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
    ) = withContext(Dispatchers.IO) {
        transaction {
            Reports.insert {
                it[Reports.appId] = appId
                it[Reports.groupId] = groupId
                it[message] = report.message
                it[stacktrace] = report.stacktrace
                it[timestamp] = Clock.System.now().toEpochMilliseconds()
                it[context] = Json.encodeToString(report.context)
                it[breadcrumbs] = Json.encodeToString(report.breadcrumbs)
                it[release] = report.release
                it[environment] = report.environment
            }

            return@transaction
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

    override suspend fun activity(
        groupIds: List<Long>,
        now: Long,
        days: Int,
    ): Map<Long, GroupActivity> {
        if (groupIds.isEmpty()) return emptyMap()

        val ids = groupIds.joinToString(",")
        val dayMillis = 24L * 60 * 60 * 1000
        val windowStart = now - days * dayMillis

        return withContext(Dispatchers.IO) {
            transaction {
                val buckets = mutableMapOf<Long, MutableList<Int>>()
                exec(
                    """
                    SELECT group_id,
                           CAST(($now - timestamp) / $dayMillis AS INTEGER) AS days_ago,
                           COUNT(*) AS crashes
                    FROM reports
                    WHERE group_id IN ($ids) AND timestamp >= $windowStart AND timestamp <= $now
                    GROUP BY group_id, days_ago
                    """.trimIndent(),
                ) { rows ->
                    while (rows.next()) {
                        val daysAgo = rows.getInt("days_ago")
                        if (daysAgo in 0 until days) {
                            val series =
                                buckets.getOrPut(rows.getLong("group_id")) { MutableList(days) { 0 } }
                            series[days - 1 - daysAgo] = rows.getInt("crashes")
                        }
                    }
                }

                val activity = mutableMapOf<Long, GroupActivity>()
                exec(
                    """
                    SELECT group_id,
                           MIN(release) AS first_release,
                           MAX(release) AS last_release,
                           MIN(environment) AS first_environment,
                           MAX(environment) AS last_environment
                    FROM reports
                    WHERE group_id IN ($ids)
                    GROUP BY group_id
                    """.trimIndent(),
                ) { rows ->
                    while (rows.next()) {
                        val groupId = rows.getLong("group_id")
                        val firstEnvironment = rows.getString("first_environment")
                        val lastEnvironment = rows.getString("last_environment")

                        activity[groupId] =
                            GroupActivity(
                                groupId = groupId,
                                dailyCrashes = buckets[groupId] ?: List(days) { 0 },
                                environment = firstEnvironment.takeIf { it == lastEnvironment },
                                firstRelease = rows.getString("first_release"),
                                lastRelease = rows.getString("last_release"),
                            )
                    }
                }

                activity
            }
        }
    }

    override suspend fun getReportById(reportId: Long): Report? =
        withContext(Dispatchers.IO) {
            transaction {
                Reports
                    .selectAll()
                    .where { Reports.id eq reportId }
                    .map { rowToReport(it) }
                    .singleOrNull()
            }
        }

    private fun rowToReport(row: ResultRow): Report =
        Report(
            row[Reports.id].value,
            row[Reports.message],
            row[Reports.stacktrace],
            Instant.fromEpochMilliseconds(row[Reports.timestamp]).toLocalDateTime(TimeZone.currentSystemDefault()),
            row[Reports.context]?.let { Json.decodeFromString(it) },
            row[Reports.breadcrumbs]?.let { Json.decodeFromString(it) },
            row[Reports.release],
            row[Reports.environment],
        )
}
