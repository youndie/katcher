package ru.workinprogress.katcher.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ErrorGroupViewedRepository
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.error.data.ErrorGroupViewedRepositoryImpl
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorGroupRepositoryTest : RepositoryTest() {
    private lateinit var repository: ErrorGroupRepository
    private lateinit var appRepository: AppRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewedRepository: ErrorGroupViewedRepository

    private var appId = 0
    private var userId = 0

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()

            appRepository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            userRepository = UserRepositoryImpl(db, UsersCrudRepositoryImpl)
            viewedRepository = ErrorGroupViewedRepositoryImpl(db)
            repository = ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl)

            val app = appRepository.create("test-app", AppType.ANDROID)
            appId = app.id

            val user = userRepository.create("test@mail.com", "Test User")
            userId = user.id
        }

    @Test
    fun `test insert and findById`() =
        runTest {
            val params =
                CreateErrorGroupParams(
                    appId = appId,
                    fingerprint = "fingerprint-1",
                    title = "Error Title",
                )
            val inserted = repository.insert(params)

            assertEquals(appId, inserted.appId)
            assertEquals("fingerprint-1", inserted.fingerprint)
            assertEquals("Error Title", inserted.title)
            assertEquals(0, inserted.occurrences)
            assertFalse(inserted.resolved)

            val found = repository.findById(inserted.id)
            assertNotNull(found)
            assertEquals(inserted.id, found.id)
            assertEquals(inserted.title, found.title)
        }

    @Test
    fun `test findByFingerprint`() =
        runTest {
            val fingerprint = "unique-fingerprint"
            repository.insert(
                CreateErrorGroupParams(
                    appId = appId,
                    fingerprint = fingerprint,
                    title = "Error",
                ),
            )

            val found = repository.findByFingerprint(appId, fingerprint)
            assertNotNull(found)
            assertEquals(fingerprint, found.fingerprint)

            val notFound = repository.findByFingerprint(appId, "other-fingerprint")
            assertNull(notFound)
        }

    @Test
    fun `test updateOccurrences`() =
        runTest {
            val group =
                repository.insert(
                    CreateErrorGroupParams(
                        appId = appId,
                        fingerprint = "fp",
                        title = "Title",
                    ),
                )
            repository.updateOccurrences(group.id)
            repository.updateOccurrences(group.id)
            2
            val updated = repository.findById(group.id)
            assertNotNull(updated)
            assertEquals(2, updated.occurrences)
            assertTrue(updated.lastSeen >= group.lastSeen)
        }

    @Test
    fun `test resolve`() =
        runTest {
            val group =
                repository.insert(
                    CreateErrorGroupParams(
                        appId = appId,
                        fingerprint = "fp",
                        title = "Title",
                    ),
                )
            assertFalse(group.resolved)

            repository.resolve(group.id)

            val resolved = repository.findById(group.id)
            assertNotNull(resolved)
            assertTrue(resolved.resolved)
        }

    @Test
    fun `test findByAppId with viewed status`() =
        runTest {
            val g1 =
                repository.insert(
                    CreateErrorGroupParams(
                        appId = appId,
                        fingerprint = "fp1",
                        title = "Error 1",
                    ),
                )
            val g2 =
                repository.insert(
                    CreateErrorGroupParams(
                        appId = appId,
                        fingerprint = "fp2",
                        title = "Error 2",
                    ),
                )

            // Mark g1 as viewed by user
            viewedRepository.updateVisitedAt(g1.id, userId)

            val result =
                repository.findByAppId(
                    appId = appId,
                    userId = userId,
                    page = 1,
                    pageSize = 10,
                    sortBy = ErrorGroupSort.id,
                    sortOrder = ErrorGroupSortOrder.asc,
                )

            assertEquals(2, result.items.size)
            assertEquals(1, result.totalPages)

            val item1 = result.items.find { it.errorGroup.id == g1.id }
            assertNotNull(item1)
            assertTrue(item1.viewed)

            val item2 = result.items.find { it.errorGroup.id == g2.id }
            assertNotNull(item2)
            assertFalse(item2.viewed)
        }

    @Test
    fun `test findByAppId pagination and sorting`() =
        runTest {
            for (i in 1..5) {
                repository.insert(
                    CreateErrorGroupParams(
                        appId = appId,
                        fingerprint = "fp-$i",
                        title = "Error $i",
                    ),
                )
            }

            val result =
                repository.findByAppId(
                    appId = appId,
                    userId = userId,
                    page = 1,
                    pageSize = 2,
                    sortBy = ErrorGroupSort.title,
                    sortOrder = ErrorGroupSortOrder.desc,
                )

            assertEquals(2, result.items.size)
            assertEquals(3, result.totalPages) // (5 + 2 - 1) / 2 = 3
            assertEquals("Error 5", result.items[0].errorGroup.title)
            assertEquals("Error 4", result.items[1].errorGroup.title)
        }
}
