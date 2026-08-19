package ru.workinprogress.feature.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.workinprogress.feature.app.AppOverview
import ru.workinprogress.feature.app.AppOverviewRepository
import java.sql.ResultSet

/**
 * Plain SQL rather than the Exposed DSL: these are three grouped aggregates that have to
 * stay identical to the native implementation, and the DSL hides the difference.
 */
class AppOverviewRepositoryImpl : AppOverviewRepository {
    override suspend fun overview(
        userId: Int,
        now: Long,
    ): Map<Int, AppOverview> =
        withContext(Dispatchers.IO) {
            transaction {
                val windowStart = now - AppOverview.DAYS * DAY_MILLIS
                val buckets = mutableMapOf<Int, MutableList<Int>>()

                query(
                    """
                    SELECT app_id,
                           CAST(($now - timestamp) / $DAY_MILLIS AS INTEGER) AS days_ago,
                           COUNT(*) AS crashes
                    FROM reports
                    WHERE timestamp >= $windowStart AND timestamp <= $now
                    GROUP BY app_id, days_ago
                    """.trimIndent(),
                ) { row ->
                    val daysAgo = row.getInt("days_ago")
                    if (daysAgo in 0 until AppOverview.DAYS) {
                        val series =
                            buckets.getOrPut(row.getInt("app_id")) { MutableList(AppOverview.DAYS) { 0 } }
                        series[AppOverview.DAYS - 1 - daysAgo] = row.getInt("crashes")
                    }
                }

                val unseen = mutableMapOf<Int, Int>()
                query(
                    """
                    SELECT g.app_id AS app_id, COUNT(*) AS unseen
                    FROM error_groups g
                    LEFT JOIN user_error_group_viewed v
                        ON v.group_id = g.id AND v.user_id = $userId
                    WHERE v.viewed_at IS NULL
                    GROUP BY g.app_id
                    """.trimIndent(),
                ) { row -> unseen[row.getInt("app_id")] = row.getInt("unseen") }

                val overviews = mutableMapOf<Int, AppOverview>()
                query(
                    """
                    SELECT app_id,
                           MAX(last_seen) AS last_seen,
                           SUM(CASE WHEN first_seen >= ${now - DAY_MILLIS} THEN 1 ELSE 0 END) AS new_today,
                           SUM(CASE WHEN fix_url IS NOT NULL AND resolved = FALSE THEN 1 ELSE 0 END) AS fixes
                    FROM error_groups
                    GROUP BY app_id
                    """.trimIndent(),
                ) { row ->
                    val appId = row.getInt("app_id")
                    val lastSeen = row.getLong("last_seen").takeUnless { row.wasNull() }

                    overviews[appId] =
                        AppOverview(
                            appId = appId,
                            unseenGroups = unseen[appId] ?: 0,
                            dailyCrashes = buckets[appId] ?: List(AppOverview.DAYS) { 0 },
                            lastCrashAt = lastSeen,
                            newGroupsToday = row.getInt("new_today"),
                            fixesWaiting = row.getInt("fixes"),
                        )
                }

                overviews
            }
        }

    // Every value interpolated below is a number this class computed itself, so there is
    // nothing here for a bind parameter to protect.
    private fun JdbcTransaction.query(
        sql: String,
        read: (ResultSet) -> Unit,
    ) {
        exec(sql) { rows ->
            while (rows.next()) read(rows)
        }
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
