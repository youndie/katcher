package io.github.youndie.katcher.data

import kotlinx.coroutines.test.runTest
import io.github.youndie.katcher.feature.app.AppRepository
import io.github.youndie.katcher.feature.app.AppType
import io.github.youndie.katcher.feature.app.data.AppKeyRepositoryImpl
import io.github.youndie.katcher.feature.app.data.AppRepositoryImpl
import io.github.youndie.katcher.feature.error.CreateErrorGroupParams
import io.github.youndie.katcher.feature.error.data.ErrorGroupRepositoryImpl
import io.github.youndie.katcher.feature.error.data.ErrorGroupViewedRepositoryImpl
import io.github.youndie.katcher.feature.report.CreateReportParams
import io.github.youndie.katcher.feature.report.data.ReportRepositoryImpl
import io.github.youndie.katcher.feature.user.data.UserRepositoryImpl
import io.github.youndie.katcher.db.AppsCrudRepositoryImpl
import io.github.youndie.katcher.db.ErrorGroupCrudRepositoryImpl
import io.github.youndie.katcher.db.UsersCrudRepositoryImpl
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
