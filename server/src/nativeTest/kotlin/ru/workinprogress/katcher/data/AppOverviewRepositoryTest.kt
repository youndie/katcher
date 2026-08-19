package ru.workinprogress.katcher.data

import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppOverview
import ru.workinprogress.feature.app.AppOverviewRepository
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppOverviewRepositoryImpl
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.user.UserRepository
import ru.workinprogress.feature.user.data.UserRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.UsersCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every timestamp here is written by the test rather than by the clock: the numbers on a card
 * are cut into day buckets, and a test that cannot say when a crash happened cannot check
 * which bucket it landed in.
 */
class AppOverviewRepositoryTest : RepositoryTest() {
    private lateinit var repository: AppOverviewRepository
    private lateinit var appRepository: AppRepository
    private lateinit var userRepository: UserRepository

    private var busyAppId = 0
    private var silentAppId = 0
    private var userId = 0

    private val now = 1_755_600_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()

            appRepository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            userRepository = UserRepositoryImpl(db, UsersCrudRepositoryImpl)
            repository = AppOverviewRepositoryImpl(db)

            busyAppId = appRepository.create("busy", AppType.JVM).id
            silentAppId = appRepository.create("silent", AppType.OTHER).id
            userId = userRepository.create("test@mail.com", "Test User").id
        }

    @Test
    fun `numbers are cut into day buckets relative to the given now`() =
        runTest {
            insertGroup(id = 1, appId = busyAppId, firstSeen = now - 2 * hour, lastSeen = now - 5 * minute)
            insertReport(groupId = 1, appId = busyAppId, timestamp = now - 30 * minute)
            insertReport(groupId = 1, appId = busyAppId, timestamp = now - hour)
            insertReport(groupId = 1, appId = busyAppId, timestamp = now - 2 * hour)
            insertReport(groupId = 1, appId = busyAppId, timestamp = now - 3 * day)
            insertReport(groupId = 1, appId = busyAppId, timestamp = now - 3 * day - hour)
            // Older than the window the card draws — it must not leak into the first bucket.
            insertReport(groupId = 1, appId = busyAppId, timestamp = now - 9 * day)

            val overview = repository.overview(userId, now).getValue(busyAppId)

            assertEquals(listOf(0, 0, 0, 2, 0, 0, 3), overview.dailyCrashes)
            assertEquals(3, overview.crashes24h)
            assertEquals(AppOverview.DAYS, overview.dailyCrashes.size)
        }

    @Test
    fun `unseen counts only the groups this user has not opened`() =
        runTest {
            insertGroup(id = 1, appId = busyAppId, firstSeen = now - 2 * hour, lastSeen = now - 5 * minute)
            insertGroup(id = 2, appId = busyAppId, firstSeen = now - 10 * day, lastSeen = now - 3 * day)
            insertViewed(groupId = 2, userId = userId, viewedAt = now - hour)

            assertEquals(1, repository.overview(userId, now).getValue(busyAppId).unseenGroups)
        }

    @Test
    fun `new today counts fresh groups and fixes count only unresolved links`() =
        runTest {
            insertGroup(id = 1, appId = busyAppId, firstSeen = now - 2 * hour, lastSeen = now - 5 * minute)
            insertGroup(
                id = 2,
                appId = busyAppId,
                firstSeen = now - 10 * day,
                lastSeen = now - 3 * day,
                fixUrl = "https://example.com/pull/1",
            )
            // A fix on a group already resolved is not waiting for anybody.
            insertGroup(
                id = 3,
                appId = busyAppId,
                firstSeen = now - 11 * day,
                lastSeen = now - 4 * day,
                fixUrl = "https://example.com/pull/2",
                resolved = true,
            )

            val overview = repository.overview(userId, now).getValue(busyAppId)

            assertEquals(1, overview.newGroupsToday)
            assertEquals(1, overview.fixesWaiting)
        }

    @Test
    fun `last crash is the newest group activity rather than the newest report`() =
        runTest {
            insertGroup(id = 1, appId = busyAppId, firstSeen = now - 10 * day, lastSeen = now - 3 * day)

            val overview = repository.overview(userId, now).getValue(busyAppId)

            assertEquals(now - 3 * day, overview.lastCrashAt)
            assertEquals(List(AppOverview.DAYS) { 0 }, overview.dailyCrashes)
        }

    @Test
    fun `an app nothing arrived for is absent and reads as never reported`() =
        runTest {
            insertGroup(id = 1, appId = busyAppId, firstSeen = now - 2 * hour, lastSeen = now - 5 * minute)

            val overviews = repository.overview(userId, now)

            assertTrue(silentAppId !in overviews)
            assertNull(AppOverview.silent(silentAppId).lastCrashAt)
            assertTrue(AppOverview.silent(silentAppId).neverReported)
        }

    private suspend fun insertGroup(
        id: Long,
        appId: Int,
        firstSeen: Long,
        lastSeen: Long,
        fixUrl: String? = null,
        resolved: Boolean = false,
    ) {
        TransactionContext.withCurrent(db) {
            execute(
                Statement
                    .create(
                        """
                        INSERT INTO error_groups
                            (id, app_id, fingerprint, title, occurrences, first_seen, last_seen, resolved, fix_url)
                        VALUES (:id, :appId, :fingerprint, :title, 1, :firstSeen, :lastSeen, :resolved, :fixUrl)
                        """.trimIndent(),
                    ).apply {
                        bind("id", id)
                        bind("appId", appId)
                        bind("fingerprint", "fingerprint-$id")
                        bind("title", "group $id")
                        bind("firstSeen", firstSeen)
                        bind("lastSeen", lastSeen)
                        bind("resolved", resolved)
                        bind("fixUrl", fixUrl)
                    },
            )
        }
    }

    private suspend fun insertReport(
        groupId: Long,
        appId: Int,
        timestamp: Long,
    ) {
        TransactionContext.withCurrent(db) {
            execute(
                Statement
                    .create(
                        """
                        INSERT INTO reports (app_id, group_id, message, stacktrace, timestamp)
                        VALUES (:appId, :groupId, :message, :stacktrace, :timestamp)
                        """.trimIndent(),
                    ).apply {
                        bind("appId", appId)
                        bind("groupId", groupId)
                        bind("message", "boom")
                        bind("stacktrace", "java.lang.IllegalStateException: boom")
                        bind("timestamp", timestamp)
                    },
            )
        }
    }

    private suspend fun insertViewed(
        groupId: Long,
        userId: Int,
        viewedAt: Long,
    ) {
        TransactionContext.withCurrent(db) {
            execute(
                Statement
                    .create(
                        """
                        INSERT INTO user_error_group_viewed (group_id, user_id, viewed_at)
                        VALUES (:groupId, :userId, :viewedAt)
                        """.trimIndent(),
                    ).apply {
                        bind("groupId", groupId)
                        bind("userId", userId)
                        bind("viewedAt", viewedAt)
                    },
            )
        }
    }
}
