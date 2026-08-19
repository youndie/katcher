package ru.workinprogress.feature.report.data

import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.GroupActivity
import ru.workinprogress.feature.report.ReleaseCount
import ru.workinprogress.feature.report.Report
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.ReportsPaginated
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object ReportRowMapper : RowMapper<Report> {
    @OptIn(ExperimentalTime::class)
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ): Report =
        Report(
            id = row.get("id").asLong(),
            message = row.get("message").asString(),
            stacktrace = row.get("stacktrace").asString(),
            timestamp =
                Instant
                    .fromEpochMilliseconds(row.get("timestamp").asLong())
                    .toLocalDateTime(currentSystemDefault()),
            context = row.get("context").asStringOrNull()?.let { Json.decodeFromString(it) },
            breadcrumbs = row.get("breadcrumbs").asStringOrNull()?.let { Json.decodeFromString(it) },
            release = row.get("release").asStringOrNull(),
            environment = row.get("environment").asStringOrNull(),
        )
}

@OptIn(ExperimentalTime::class)
class ReportRepositoryImpl(
    private val db: ISQLite,
) : ReportRepository {
    override suspend fun insert(
        appId: Int,
        groupId: Long,
        report: CreateReportParams,
    ) = TransactionContext.withCurrent(db) {
        execute(
            Statement
                .create(
                    """
    INSERT INTO reports (app_id, group_id, message, stacktrace, timestamp, context, breadcrumbs, release, environment)
    VALUES (:appId, :groupId, :message, :stacktrace, :timestamp, :context, :breadcrumbs, :release, :environment)
    """,
                ).apply {
                    bind("appId", appId)
                    bind("groupId", groupId)
                    bind("message", report.message)
                    bind("stacktrace", report.stacktrace)
                    bind("timestamp", Clock.System.now().toEpochMilliseconds())
                    bind("context", report.context?.let { Json.encodeToString(it) })
                    bind("breadcrumbs", report.breadcrumbs?.let { Json.encodeToString(it) })
                    bind("release", report.release)
                    bind("environment", report.environment)
                },
        )
        return@withCurrent
    }

    override suspend fun findByApp(
        appId: Int,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated =
        TransactionContext.withCurrent(db) {
            val safePageSize = pageSize.coerceIn(1, 100)
            val safePage = page.coerceAtLeast(1)
            val offset = (safePage - 1) * safePageSize

            val selectSql =
                """
                SELECT *
                FROM reports
                WHERE app_id = :appId
                ORDER BY timestamp DESC
                LIMIT $safePageSize OFFSET $offset
                """.trimIndent()

            val reports =
                fetchAll(
                    Statement.create(selectSql).apply {
                        bind("appId", appId)
                    },
                    ReportRowMapper,
                ).getOrNull()
                    .orEmpty()

            val countSql =
                """
                SELECT COUNT(*) AS c
                FROM reports
                WHERE app_id = :appId
                """.trimIndent()

            val total =
                fetchAll(
                    Statement.create(countSql).apply {
                        bind("appId", appId)
                    },
                    CountMapper,
                ).getOrThrow()
                    .first()

            ReportsPaginated(
                items = reports,
                page = safePage,
                totalPages = ((total + safePageSize - 1) / safePageSize).toInt(),
            )
        }

    override suspend fun findByGroup(
        groupId: Long,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated =
        TransactionContext.withCurrent(db) {
            val safePageSize = pageSize.coerceIn(1, 100)
            val safePage = page.coerceAtLeast(1)
            val offset = (safePage - 1) * safePageSize

            val selectSql =
                """
                SELECT *
                FROM reports
                WHERE group_id = :groupId
                ORDER BY timestamp DESC
                LIMIT $safePageSize OFFSET $offset
                """.trimIndent()

            val reports =
                fetchAll(
                    Statement.create(selectSql).apply {
                        bind("groupId", groupId)
                    },
                    ReportRowMapper,
                ).getOrNull()
                    .orEmpty()

            val countSql =
                """
                SELECT COUNT(*) AS c
                FROM reports
                WHERE group_id = :groupId
                """.trimIndent()

            val total =
                fetchAll(
                    Statement.create(countSql).apply {
                        bind("groupId", groupId)
                    },
                    CountMapper,
                ).getOrThrow()
                    .first()

            ReportsPaginated(
                items = reports,
                page = safePage,
                totalPages = ((total + safePageSize - 1) / safePageSize).toInt(),
            )
        }

    override suspend fun activity(
        groupIds: List<Long>,
        now: Long,
        days: Int,
    ): Map<Long, GroupActivity> {
        if (groupIds.isEmpty()) return emptyMap()

        // Ids come from a page of rows this server just read, so they are numbers by
        // construction; there is nothing here a bind parameter would protect.
        val ids = groupIds.joinToString(",")
        val dayMillis = 24L * 60 * 60 * 1000
        val windowStart = now - days * dayMillis

        return TransactionContext.withCurrent(db) {
            val buckets = mutableMapOf<Long, MutableList<Int>>()
            fetchAll(
                Statement.create(
                    """
                    SELECT group_id,
                           CAST(($now - timestamp) / $dayMillis AS INTEGER) AS days_ago,
                           COUNT(*) AS crashes
                    FROM reports
                    WHERE group_id IN ($ids) AND timestamp >= $windowStart AND timestamp <= $now
                    GROUP BY group_id, days_ago
                    """.trimIndent(),
                ),
            ).getOrThrow()
                .rows
                .forEach { row ->
                    val daysAgo = row.get("days_ago").asInt()
                    if (daysAgo in 0 until days) {
                        val series = buckets.getOrPut(row.get("group_id").asLong()) { MutableList(days) { 0 } }
                        series[days - 1 - daysAgo] = row.get("crashes").asInt()
                    }
                }

            val activity = mutableMapOf<Long, GroupActivity>()
            fetchAll(
                Statement.create(
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
                ),
            ).getOrThrow()
                .rows
                .forEach { row ->
                    val groupId = row.get("group_id").asLong()
                    val firstEnvironment = row.get("first_environment").asStringOrNull()
                    val lastEnvironment = row.get("last_environment").asStringOrNull()

                    activity[groupId] =
                        GroupActivity(
                            groupId = groupId,
                            dailyCrashes = buckets[groupId] ?: List(days) { 0 },
                            environment = firstEnvironment.takeIf { it == lastEnvironment },
                            firstRelease = row.get("first_release").asStringOrNull(),
                            lastRelease = row.get("last_release").asStringOrNull(),
                        )
                }

            activity
        }
    }

    override suspend fun releases(
        groupId: Long,
        limit: Int,
    ): List<ReleaseCount> =
        TransactionContext.withCurrent(db) {
            fetchAll(
                Statement
                    .create(
                        """
                        SELECT release, COUNT(*) AS crashes
                        FROM reports
                        WHERE group_id = :groupId AND release IS NOT NULL
                        GROUP BY release
                        ORDER BY crashes DESC
                        LIMIT $limit
                        """.trimIndent(),
                    ).apply {
                        bind("groupId", groupId)
                    },
            ).getOrThrow()
                .rows
                .map { row ->
                    ReleaseCount(
                        release = row.get("release").asString(),
                        count = row.get("crashes").asInt(),
                    )
                }
        }

    override suspend fun getReportById(reportId: Long) =
        TransactionContext.withCurrent(db) {
            val selectSql = """SELECT * FROM reports WHERE id = :reportId"""

            fetchAll(
                Statement.create(selectSql).apply {
                    bind("reportId", reportId)
                },
                ReportRowMapper,
            ).map { it.firstOrNull() }.getOrNull()
        }
}

private object CountMapper : RowMapper<Long> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ): Long = row.get("c").asLong()
}
