@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.error.data

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.ResultSet.Row
import io.github.smyrgeorge.sqlx4k.ResultSet.Row.Column
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import io.github.smyrgeorge.sqlx4k.impl.extensions.asBoolean
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.ErrorGroup
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ErrorGroupViewedRepository
import ru.workinprogress.feature.error.ErrorGroupWithViewed
import ru.workinprogress.feature.error.ErrorGroupsPaginated
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ErrorGroupRepositoryImpl(
    private val db: ISQLite,
    private val crudRepository: ErrorGroupCrudRepository,
) : ErrorGroupRepository {
    override suspend fun findByFingerprint(
        appId: Int,
        fingerprint: String,
    ): ErrorGroup? =
        db.transaction {
            crudRepository.findOneByFingerprint(db, appId, fingerprint).getOrNull()?.toDomain()
        }

    override suspend fun insert(newGroup: CreateErrorGroupParams): ErrorGroup =
        db.transaction {
            crudRepository
                .insert(
                    db,
                    ErrorGroupDb(
                        id = 0,
                        appId = newGroup.appId,
                        fingerprint = newGroup.fingerprint,
                        title = newGroup.title,
                        occurrences = 1,
                        firstSeen = Clock.System.now().toEpochMilliseconds(),
                        lastSeen = Clock.System.now().toEpochMilliseconds(),
                        resolved = false,
                    ),
                ).getOrThrow()
                .toDomain()
        }

    override suspend fun findById(groupId: Long): ErrorGroup? =
        db.transaction {
            crudRepository.findOneById(db, groupId).getOrNull()?.toDomain()
        }

    override suspend fun updateOccurrences(id: Long) {
        db.transaction {
            db.execute(
                Statement
                    .create(
                        "UPDATE error_groups SET occurrences = occurrences + 1 WHERE id = :id",
                    ).apply {
                        bind("id", id)
                    },
            )
            db.execute(
                Statement
                    .create(
                        "UPDATE error_groups SET last_seen = :lastSeen WHERE id = :id",
                    ).apply {
                        bind("id", id)
                        bind("lastSeen", Clock.System.now().toEpochMilliseconds())
                        bind("id", id)
                    },
            )
        }
    }

    override suspend fun resolve(groupId: Long) {
        db.transaction {
            db.execute(
                Statement
                    .create(
                        "UPDATE error_groups SET resolved = :resolved WHERE id = :id",
                    ).apply {
                        bind("id", groupId)
                        bind("resolved", true)
                    },
            )
        }
    }

    override suspend fun findByAppId(
        appId: Int,
        userId: Int,
        page: Int,
        pageSize: Int,
        sortBy: ErrorGroupSort,
        sortOrder: ErrorGroupSortOrder,
    ): ErrorGroupsPaginated =
        db.transaction {
            val safePageSize = pageSize.coerceIn(1, 100)
            val safePage = page.coerceAtLeast(1)
            val offset = (safePage - 1) * safePageSize

            val sortField =
                when (sortBy) {
                    ErrorGroupSort.id -> "id"
                    ErrorGroupSort.title -> "title"
                    ErrorGroupSort.occurrences -> "occurrences"
                    ErrorGroupSort.lastSeen -> "last_seen"
                }

            val order =
                when (sortOrder) {
                    ErrorGroupSortOrder.asc -> "ASC"
                    ErrorGroupSortOrder.desc -> "DESC"
                }

            val selectSql =
                """
                SELECT 
                    g.*,
                    CASE WHEN v.viewed_at IS NOT NULL THEN 1 ELSE 0 END AS viewed
                FROM error_groups g
                LEFT JOIN user_error_group_viewed v
                    ON v.group_id = g.id AND v.user_id = :userId
                WHERE g.app_id = :appId
                ORDER BY $sortField $order
                LIMIT $pageSize OFFSET $offset
                """.trimIndent()

            val items =
                db
                    .fetchAll(
                        Statement.create(selectSql).apply {
                            bind("appId", appId)
                            bind("userId", userId)
                        },
                        ErrorGroupWithViewedRowMapper,
                    ).getOrThrow()

            val total = crudRepository.countByAppId(db, appId).getOrThrow()

            ErrorGroupsPaginated(
                items = items,
                page = page,
                totalPages = ((total + pageSize - 1) / pageSize).toInt(),
                sortBy = sortBy,
                sortOrder = sortOrder,
            )
        }
}

@OptIn(ExperimentalTime::class)
class ErrorGroupViewedRepositoryImpl(
    private val db: ISQLite,
) : ErrorGroupViewedRepository {
    override suspend fun updateVisitedAt(
        errorGroupId: Long,
        forUserId: Int,
    ) {
        db.transaction {
            db.execute(
                Statement
                    .create(
                        """INSERT INTO user_error_group_viewed(group_id, user_id, viewed_at) VALUES (:groupId, :userId, :viewedAt)""",
                    ).apply {
                        bind("groupId", errorGroupId)
                        bind("userId", forUserId)
                        bind("viewedAt", Clock.System.now().toEpochMilliseconds())
                    },
            )
        }
    }
}

@Table("error_groups")
data class ErrorGroupDb(
    @Id(insert = false)
    val id: Long,
    val appId: Int,
    val fingerprint: String,
    val title: String,
    val occurrences: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val resolved: Boolean,
)

fun ErrorGroupDb.toDomain() =
    ErrorGroup(
        id,
        appId,
        fingerprint,
        title,
        Instant.fromEpochMilliseconds(firstSeen).toLocalDateTime(TimeZone.currentSystemDefault()),
        Instant.fromEpochMilliseconds(lastSeen).toLocalDateTime(TimeZone.currentSystemDefault()),
        occurrences,
        resolved,
    )

object ErrorGroupRowMapper : RowMapper<ErrorGroupDb> {
    override fun map(row: Row): ErrorGroupDb {
        val id: Column = row.get("id")
        val appId: Column = row.get("app_id")
        val fingerprint: Column = row.get("fingerprint")
        val title: Column = row.get("title")
        val occurrences: Column = row.get("occurrences")
        val firstSeen: Column = row.get("first_seen")
        val lastSeen: Column = row.get("last_seen")
        val resolved: Column = row.get("resolved")

        return ErrorGroupDb(
            id = id.asLong(),
            appId = appId.asInt(),
            fingerprint = fingerprint.asString(),
            title = title.asString(),
            occurrences = occurrences.asInt(),
            firstSeen = firstSeen.asLong(),
            lastSeen = lastSeen.asLong(),
            resolved = resolved.asBoolean(),
        )
    }
}

object ErrorGroupWithViewedRowMapper : RowMapper<ErrorGroupWithViewed> {
    override fun map(row: Row): ErrorGroupWithViewed {
        val group = ErrorGroupRowMapper.map(row).toDomain()
        val viewed = row.get("viewed").asInt() == 1
        return ErrorGroupWithViewed(
            errorGroup = group,
            viewed = viewed,
        )
    }
}

@Repository(mapper = ErrorGroupRowMapper::class)
interface ErrorGroupCrudRepository : CrudRepository<ErrorGroupDb> {
    @Query("SELECT * FROM error_groups WHERE id = :id LIMIT 1")
    suspend fun findOneById(
        context: QueryExecutor,
        id: Long,
    ): Result<ErrorGroupDb?>

    @Query("SELECT * FROM error_groups WHERE app_id = :appId AND fingerprint = :fingerprint LIMIT 1")
    suspend fun findOneByFingerprint(
        context: QueryExecutor,
        appId: Int,
        fingerprint: String,
    ): Result<ErrorGroupDb?>

    @Query("SELECT COUNT(*) AS c FROM error_groups WHERE app_id = :appId")
    suspend fun countByAppId(
        context: QueryExecutor,
        appId: Int,
    ): Result<Long>
}
