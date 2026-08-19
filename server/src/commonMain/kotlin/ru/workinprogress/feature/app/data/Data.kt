package ru.workinprogress.feature.app.data

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asIntOrNull
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLongOrNull
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppOverview
import ru.workinprogress.feature.app.AppOverviewRepository
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Table("apps")
data class AppDb(
    @Id(insert = false)
    val id: Int,
    val name: String,
    val apiKey: String,
    val type: String,
)

object AppRowMapper : RowMapper<AppDb> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ): AppDb {
        val id: ResultSet.Row.Column = row.get("id")
        val name: ResultSet.Row.Column = row.get("name")
        val apiKey: ResultSet.Row.Column = row.get("api_key")
        val type: ResultSet.Row.Column = row.get("type")

        return AppDb(id = id.asInt(), name = name.asString(), apiKey = apiKey.asString(), type = type.asString())
    }
}

fun AppDb.toDomain() = App(id = id, name = name, apiKey = apiKey, type = AppType.valueOf(type))

@OptIn(ExperimentalUuidApi::class)
class AppRepositoryImpl(
    private val db: ISQLite,
    private val crudRepository: AppsCrudRepository,
) : AppRepository {
    override suspend fun create(
        name: String,
        type: AppType,
    ): App =
        TransactionContext.withCurrent(db) {
            val apiKey = Uuid.random().toString().replace("-", "")

            crudRepository
                .insert(this, AppDb(id = 0, name = name, apiKey = apiKey, type = type.name))
                .getOrThrow()
                .toDomain()
        }

    override suspend fun findByApiKey(apiKey: String): App? =
        TransactionContext.withCurrent(db) {
            crudRepository
                .findOneByApiKey(this, apiKey)
                .getOrNull()
                ?.toDomain()
        }

    override suspend fun findAll(): List<App> =
        TransactionContext.withCurrent(db) {
            crudRepository.findAll(this).getOrThrow().map { it.toDomain() }
        }

    override suspend fun findById(id: Int): App? =
        TransactionContext.withCurrent(db) {
            crudRepository.findOneById(this, id).getOrNull()?.toDomain()
        }
}

@Repository(mapper = AppRowMapper::class)
interface AppsCrudRepository : CrudRepository<AppDb> {
    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun findOneById(
        context: QueryExecutor,
        id: Int,
    ): Result<AppDb?>

    @Query("SELECT * FROM apps WHERE api_key = :apiKey")
    suspend fun findOneByApiKey(
        context: QueryExecutor,
        apiKey: String,
    ): Result<AppDb?>

    @Query("SELECT * FROM apps")
    suspend fun findAll(context: QueryExecutor): Result<List<AppDb>>
}

/**
 * Three grouped queries, not one per card: the list page renders every app at once, and a
 * per-card query would multiply with the number of apps for numbers nobody scrolls to.
 */
class AppOverviewRepositoryImpl(
    private val db: ISQLite,
) : AppOverviewRepository {
    override suspend fun overview(
        userId: Int,
        now: Long,
    ): Map<Int, AppOverview> =
        TransactionContext.withCurrent(db) {
            val windowStart = now - AppOverview.DAYS * DAY_MILLIS

            val buckets = mutableMapOf<Int, MutableList<Int>>()
            fetchAll(
                Statement
                    .create(
                        """
                        SELECT app_id,
                               CAST((:now - timestamp) / :day AS INTEGER) AS days_ago,
                               COUNT(*) AS crashes
                        FROM reports
                        WHERE timestamp >= :since AND timestamp <= :now
                        GROUP BY app_id, days_ago
                        """.trimIndent(),
                    ).apply {
                        bind("now", now)
                        bind("day", DAY_MILLIS)
                        bind("since", windowStart)
                    },
            ).getOrThrow()
                .rows
                .forEach { row ->
                    val appId = row.get("app_id").asInt()
                    val daysAgo = row.get("days_ago").asInt()
                    if (daysAgo in 0 until AppOverview.DAYS) {
                        val series = buckets.getOrPut(appId) { MutableList(AppOverview.DAYS) { 0 } }
                        // Oldest first: bucket 0 is the running day and belongs at the end.
                        series[AppOverview.DAYS - 1 - daysAgo] = row.get("crashes").asInt()
                    }
                }

            val unseen = mutableMapOf<Int, Int>()
            fetchAll(
                Statement
                    .create(
                        """
                        SELECT g.app_id, COUNT(*) AS unseen
                        FROM error_groups g
                        LEFT JOIN user_error_group_viewed v
                            ON v.group_id = g.id AND v.user_id = :userId
                        WHERE v.viewed_at IS NULL
                        GROUP BY g.app_id
                        """.trimIndent(),
                    ).apply {
                        bind("userId", userId)
                    },
            ).getOrThrow()
                .rows
                .forEach { row -> unseen[row.get("app_id").asInt()] = row.get("unseen").asInt() }

            val overviews = mutableMapOf<Int, AppOverview>()
            fetchAll(
                Statement
                    .create(
                        """
                        SELECT app_id,
                               MAX(last_seen) AS last_seen,
                               SUM(CASE WHEN first_seen >= :dayAgo THEN 1 ELSE 0 END) AS new_today,
                               SUM(CASE WHEN fix_url IS NOT NULL AND resolved = 0 THEN 1 ELSE 0 END) AS fixes
                        FROM error_groups
                        GROUP BY app_id
                        """.trimIndent(),
                    ).apply {
                        bind("dayAgo", now - DAY_MILLIS)
                    },
            ).getOrThrow()
                .rows
                .forEach { row ->
                    val appId = row.get("app_id").asInt()
                    overviews[appId] =
                        AppOverview(
                            appId = appId,
                            unseenGroups = unseen[appId] ?: 0,
                            dailyCrashes = buckets[appId] ?: List(AppOverview.DAYS) { 0 },
                            lastCrashAt = row.get("last_seen").asLongOrNull(),
                            newGroupsToday = row.get("new_today").asIntOrNull() ?: 0,
                            fixesWaiting = row.get("fixes").asIntOrNull() ?: 0,
                        )
                }

            overviews
        }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
