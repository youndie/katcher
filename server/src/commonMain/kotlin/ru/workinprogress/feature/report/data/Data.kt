package ru.workinprogress.feature.report.data

import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.Report
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.ReportsPaginated
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object ReportRowMapper : RowMapper<Report> {
    @OptIn(ExperimentalTime::class)
    override fun map(row: ResultSet.Row): Report =
        Report(
            id = row.get("id").asLong(),
            message = row.get("message").asString(),
            stacktrace = row.get("stacktrace").asString(),
            timestamp =
                Instant
                    .fromEpochMilliseconds(row.get("timestamp").asLong())
                    .toLocalDateTime(currentSystemDefault()),
            context = row.get("context").asStringOrNull()?.let { Json.decodeFromString(it) },
            release = row.get("release").asStringOrNull(),
            environment = row.get("environment").asStringOrNull(),
        )
}

class ReportRepositoryImpl(
    private val db: ISQLite,
) : ReportRepository {
    override suspend fun insert(
        appId: Int,
        groupId: Long,
        report: CreateReportParams,
    ): Report =
        db.transaction {
            db
                .fetchAll(
                    Statement
                        .create(
                            """
    INSERT INTO reports (app_id, group_id, app_key, message, stacktrace, context, release, environment)
    VALUES (:appId, :groupId, :appKey, :message, :stacktrace, :context, :release, :environment)
    """,
                        ).apply {
                            bind("appId", appId)
                            bind("groupId", groupId)
                            bind("appKey", report.appKey)
                            bind("message", report.message)
                            bind("stacktrace", report.stacktrace)
                            bind("context", report.context)
                            bind("release", report.release)
                            bind("environment", report.environment)
                        },
                    ReportRowMapper,
                ).getOrThrow()
                .first()
        }

    override suspend fun findByApp(
        appId: Int,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated =
        db.transaction {
            val offset = (page - 1) * pageSize

            val selectSql =
                """
                SELECT *
                FROM reports
                WHERE app_id = :appId
                ORDER BY timestamp DESC
                LIMIT $pageSize OFFSET $offset
                """.trimIndent()

            val reports =
                db
                    .fetchAll(
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
                db
                    .fetchAll(
                        Statement.create(countSql).apply {
                            bind("appId", appId)
                        },
                        CountMapper,
                    ).getOrThrow()
                    .first()

            ReportsPaginated(
                items = reports,
                page = page,
                totalPages = ((total + pageSize - 1) / pageSize).toInt(),
            )
        }

    override suspend fun findByGroup(
        groupId: Long,
        page: Int,
        pageSize: Int,
    ): ReportsPaginated =
        db.transaction {
            val offset = (page - 1) * pageSize

            val selectSql =
                """
                SELECT *
                FROM reports
                WHERE group_id = :groupId
                ORDER BY timestamp DESC
                LIMIT $pageSize OFFSET $offset
                """.trimIndent()

            val reports =
                db
                    .fetchAll(
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
                db
                    .fetchAll(
                        Statement.create(countSql).apply {
                            bind("groupId", groupId)
                        },
                        CountMapper,
                    ).getOrThrow()
                    .first()

            ReportsPaginated(
                items = reports,
                page = page,
                totalPages = ((total + pageSize - 1) / pageSize).toInt(),
            )
        }
}

private object CountMapper : RowMapper<Long> {
    override fun map(row: ResultSet.Row): Long = row.get("c").asLong()
}
