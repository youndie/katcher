package ru.workinprogress.katcher.data

import io.github.smyrgeorge.sqlx4k.ResultSet.Row
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ErrorGroupViewedRepository
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.error.data.ErrorGroupViewedRepositoryImpl
import ru.workinprogress.feature.user.UserRepository
import ru.workinprogress.feature.user.data.UserRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.ErrorGroupCrudRepositoryImpl
import ru.workinprogress.katcher.db.UsersCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorGroupViewedRepositoryTest : RepositoryTest() {
    private lateinit var repository: ErrorGroupViewedRepository
    private lateinit var userRepository: UserRepository
    private lateinit var appRepository: AppRepository
    private lateinit var errorGroupRepository: ErrorGroupRepository

    private var userId = 0
    private var groupId = 0L
    private var appId = 0

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()

            repository = ErrorGroupViewedRepositoryImpl(db)
            userRepository = UserRepositoryImpl(db, UsersCrudRepositoryImpl)
            appRepository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            errorGroupRepository = ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl)

            val app = appRepository.create("test-app", AppType.ANDROID)
            appId = app.id

            val user = userRepository.create("test@mail.com", "Test User")
            userId = user.id

            val errorGroup =
                errorGroupRepository.insert(
                    CreateErrorGroupParams(
                        appId = appId,
                        fingerprint = "test-fingerprint",
                        title = "Test Error",
                    ),
                )
            groupId = errorGroup.id
        }

    @Test
    fun `test updateVisitedAt upsert logic`() =
        runTest {
            repository.updateVisitedAt(groupId, userId)

            repository.updateVisitedAt(groupId, userId)

            val result =
                TransactionContext.withCurrent(db) {
                    fetchAll(
                        Statement
                            .create("SELECT viewed_at FROM user_error_group_viewed WHERE group_id = :groupId AND user_id = :userId")
                            .apply {
                                bind("groupId", groupId)
                                bind("userId", userId)
                            },
                        object : RowMapper<Long> {
                            override fun map(
                                row: Row,
                                converters: ValueEncoderRegistry,
                            ): Long = row.get("viewed_at").asLong()
                        },
                    ).getOrThrow().firstOrNull()
                }

            assertNotNull(result)
            assertTrue(result > 0)
        }

    @Test
    fun `test removeVisits`() =
        runTest {
            repository.updateVisitedAt(groupId, userId)

            val initialCheck =
                TransactionContext.withCurrent(db) {
                    fetchAll(
                        Statement
                            .create("SELECT 1 FROM user_error_group_viewed WHERE group_id = :groupId")
                            .apply {
                                bind("groupId", groupId)
                            },
                        object : RowMapper<Int> {
                            override fun map(
                                row: Row,
                                converters: ValueEncoderRegistry,
                            ): Int = 1
                        },
                    ).getOrThrow().isNotEmpty()
                }
            assertTrue(initialCheck)

            repository.removeVisits(groupId)

            val finalCheck =
                TransactionContext.withCurrent(db) {
                    fetchAll(
                        Statement
                            .create("SELECT 1 FROM user_error_group_viewed WHERE group_id = :groupId")
                            .apply {
                                bind("groupId", groupId)
                            },
                        object : RowMapper<Int> {
                            override fun map(
                                row: Row,
                                converters: ValueEncoderRegistry,
                            ): Int = row.get("1").asInt()
                        },
                    ).getOrThrow().isEmpty()
                }
            assertTrue(finalCheck)
        }
}
