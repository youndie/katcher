package ru.workinprogress.katcher.data

import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.report.ErrorGroupFilter
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder
import ru.workinprogress.feature.user.UserRepository
import ru.workinprogress.feature.user.data.UserRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.ErrorGroupCrudRepositoryImpl
import ru.workinprogress.katcher.db.UsersCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorGroupFilterTest : RepositoryTest() {
    private lateinit var repository: ErrorGroupRepository
    private lateinit var appRepository: AppRepository
    private lateinit var userRepository: UserRepository

    private var appId = 0
    private var userId = 0

    private val now = 1_755_600_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()

            appRepository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            userRepository = UserRepositoryImpl(db, UsersCrudRepositoryImpl)
            repository = ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl)

            appId = appRepository.create("billing", AppType.JVM).id
            userId = userRepository.create("test@mail.com", "Test User").id

            group(
                fingerprint = "fp-mongo",
                exceptionType = "MongoCommandException",
                message = "can't find index with key",
                location = "UserRepository.kt:64",
                lastSeen = now - 2 * day,
                environment = "production",
                release = "1.4.2",
            )
            group(
                fingerprint = "fp-null",
                exceptionType = "NullPointerException",
                message = "name is null",
                location = "ProfileMapper.kt:19",
                lastSeen = now - 20 * day,
                resolved = true,
                environment = "staging",
                release = "1.4.1",
            )
            group(
                fingerprint = "fp-timeout",
                exceptionType = "SocketTimeoutException",
                message = "timeout",
                location = "TelegramClient.kt:41",
                lastSeen = now,
                environment = "production",
                release = "1.4.1",
            )
        }

    @Test
    fun `search looks at the composed title rather than the stacktrace head`() =
        runTest {
            val found = list(ErrorGroupFilter(query = "profilemapper"))

            assertEquals(listOf("NullPointerException"), found)
        }

    @Test
    fun `unresolved only hides what somebody closed`() =
        runTest {
            val found = list(ErrorGroupFilter(unresolvedOnly = true))

            assertTrue("NullPointerException" !in found)
            assertEquals(2, found.size)
        }

    @Test
    fun `the period is measured against last seen`() =
        runTest {
            assertEquals(listOf("SocketTimeoutException"), list(ErrorGroupFilter(days = 1)))
            assertEquals(2, list(ErrorGroupFilter(days = 7)).size)
        }

    @Test
    fun `environment and release ask whether such a report exists at all`() =
        runTest {
            assertEquals(2, list(ErrorGroupFilter(environment = "production")).size)
            assertEquals(
                listOf("SocketTimeoutException", "NullPointerException"),
                list(ErrorGroupFilter(release = "1.4.1")).sorted().reversed(),
            )
        }

    @Test
    fun `counts say how many passed and how many there are`() =
        runTest {
            val page =
                repository.findByAppId(
                    appId = appId,
                    userId = userId,
                    page = 1,
                    pageSize = 15,
                    sortBy = ErrorGroupSort.id,
                    sortOrder = ErrorGroupSortOrder.desc,
                    filter = ErrorGroupFilter(unresolvedOnly = true),
                    now = now,
                )

            assertEquals(2, page.total)
            assertEquals(3, page.totalUnfiltered)
        }

    @Test
    fun `filter options come from the reports that actually arrived`() =
        runTest {
            val options = repository.filterOptions(appId)

            assertEquals(listOf("staging", "production"), options.environments)
            assertEquals(listOf("1.4.2", "1.4.1"), options.releases)
        }

    private suspend fun list(filter: ErrorGroupFilter): List<String> =
        repository
            .findByAppId(
                appId = appId,
                userId = userId,
                page = 1,
                pageSize = 15,
                sortBy = ErrorGroupSort.id,
                sortOrder = ErrorGroupSortOrder.desc,
                filter = filter,
                now = now,
            ).items
            .mapNotNull { it.errorGroup.exceptionType }

    private suspend fun group(
        fingerprint: String,
        exceptionType: String,
        message: String,
        location: String,
        lastSeen: Long,
        environment: String,
        release: String,
        resolved: Boolean = false,
    ) {
        val group =
            repository.insert(
                CreateErrorGroupParams(
                    appId = appId,
                    fingerprint = fingerprint,
                    title = "$exceptionType: $message",
                    exceptionType = exceptionType,
                    message = message,
                    location = location,
                ),
            )

        if (resolved) repository.resolve(group.id)

        TransactionContext.withCurrent(db) {
            execute(
                Statement
                    .create("UPDATE error_groups SET last_seen = :lastSeen WHERE id = :id")
                    .apply {
                        bind("id", group.id)
                        bind("lastSeen", lastSeen)
                    },
            )
            execute(
                Statement
                    .create(
                        """
                        INSERT INTO reports (app_id, group_id, message, stacktrace, timestamp, release, environment)
                        VALUES (:appId, :groupId, :message, :stacktrace, :timestamp, :release, :environment)
                        """.trimIndent(),
                    ).apply {
                        bind("appId", appId)
                        bind("groupId", group.id)
                        bind("message", message)
                        bind("stacktrace", "$exceptionType: $message")
                        bind("timestamp", lastSeen)
                        bind("release", release)
                        bind("environment", environment)
                    },
            )
        }
    }
}
