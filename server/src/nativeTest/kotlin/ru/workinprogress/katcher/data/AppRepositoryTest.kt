package ru.workinprogress.katcher.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppKeyRepositoryImpl
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.error.data.ErrorGroupViewedRepositoryImpl
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.data.ReportRepositoryImpl
import ru.workinprogress.feature.user.data.UserRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.ErrorGroupCrudRepositoryImpl
import ru.workinprogress.katcher.db.UsersCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppRepositoryTest : RepositoryTest() {
    private lateinit var repository: AppRepository

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()
            repository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
        }

    @Test
    fun `test create and findById`() =
        runTest {
            val name = "Test App"
            val type = AppType.ANDROID
            val created = repository.create(name, type)

            assertEquals(name, created.name)
            assertEquals(type, created.type)

            val found = repository.findById(created.id)
            assertNotNull(found)
            assertEquals(created.id, found.id)
            assertEquals(name, found.name)
            assertEquals(type, found.type)
        }

    @Test
    fun `test findAll`() =
        runTest {
            repository.create("App 1", AppType.ANDROID)
            repository.create("App 2", AppType.COMPOSE_MULTIPLATFORM)
            repository.create("App 3", AppType.OTHER)

            val all = repository.findAll()
            assertEquals(3, all.size)

            val names = all.map { it.name }.toSet()
            assertEquals(setOf("App 1", "App 2", "App 3"), names)
        }

    @Test
    fun `renaming keeps the app and its id`() =
        runTest {
            val created = repository.create("old name", AppType.JVM)

            repository.rename(created.id, "new name")

            val found = assertNotNull(repository.findById(created.id))
            assertEquals("new name", found.name)
        }

    @Test
    fun `deleting takes the reports and groups with it`() =
        runTest {
            val app = repository.create("doomed", AppType.JVM)
            val keys = AppKeyRepositoryImpl(db)
            val groups = ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl)
            val reports = ReportRepositoryImpl(db)
            val users = UserRepositoryImpl(db, UsersCrudRepositoryImpl)
            val viewed = ErrorGroupViewedRepositoryImpl(db)

            keys.issue(app.id, 0)
            val group =
                groups.insert(
                    CreateErrorGroupParams(appId = app.id, fingerprint = "fp", title = "boom"),
                )
            reports.insert(
                app.id,
                group.id,
                CreateReportParams(appKey = "k", message = "boom", stacktrace = "boom"),
            )
            viewed.updateVisitedAt(group.id, users.create("a@b.c", "A").id)

            val before = repository.contents(app.id)
            assertEquals(1, before.groups)
            assertEquals(1, before.reports)

            repository.delete(app.id)

            assertNull(repository.findById(app.id))
            assertEquals(0, reports.findByGroup(group.id, 1, 10).items.size)
            assertNull(groups.findById(group.id))
            assertEquals(emptyMap(), keys.listAll())
        }
}
