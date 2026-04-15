package ru.workinprogress.katcher.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.data.ReportRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.ErrorGroupCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportRepositoryTest : RepositoryTest() {
    private lateinit var repository: ReportRepository
    private lateinit var appRepository: AppRepository
    private lateinit var errorGroupRepository: ErrorGroupRepository

    private var appId = 0
    private var groupId = 0L

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()

            appRepository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            errorGroupRepository = ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl)
            repository = ReportRepositoryImpl(db)

            val app = appRepository.create("test-app", AppType.ANDROID)
            appId = app.id

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
    fun `test insert and findByApp`() =
        runTest {
            val params =
                CreateReportParams(
                    appKey = "test-key",
                    message = "Report Message",
                    stacktrace = "Stacktrace...",
                    context = mapOf("key" to "value"),
                    release = "1.0.0",
                    environment = "production",
                )

            repository.insert(appId, groupId, params)

            val result = repository.findByApp(appId, page = 1, pageSize = 10)

            assertEquals(1, result.items.size)
            val report = result.items[0]
            assertEquals("Report Message", report.message)
            assertEquals("Stacktrace...", report.stacktrace)
            assertEquals(mapOf("key" to "value"), report.context)
            assertEquals("1.0.0", report.release)
            assertEquals("production", report.environment)
            assertEquals(1, result.totalPages)
        }

    @Test
    fun `test findByGroup`() =
        runTest {
            val params =
                CreateReportParams(
                    appKey = "test-key",
                    message = "Group Report",
                    stacktrace = "Stacktrace",
                )

            repository.insert(appId, groupId, params)

            val result = repository.findByGroup(groupId, page = 1, pageSize = 10)

            assertEquals(1, result.items.size)
            assertEquals("Group Report", result.items[0].message)
        }

    @Test
    fun `test findByApp pagination`() =
        runTest {
            for (i in 1..5) {
                repository.insert(
                    appId,
                    groupId,
                    CreateReportParams(
                        appKey = "key",
                        message = "Msg $i",
                        stacktrace = "ST",
                    ),
                )
            }

            val result = repository.findByApp(appId, page = 1, pageSize = 2)

            assertEquals(2, result.items.size)
            assertEquals(3, result.totalPages)
        }

    @Test
    fun `test findByGroup pagination`() =
        runTest {
            for (i in 1..5) {
                repository.insert(
                    appId,
                    groupId,
                    CreateReportParams(
                        appKey = "key",
                        message = "Msg $i",
                        stacktrace = "ST",
                    ),
                )
            }

            val result = repository.findByGroup(groupId, page = 1, pageSize = 3)

            assertEquals(3, result.items.size)
            assertEquals(2, result.totalPages)
        }
}
