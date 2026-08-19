package ru.workinprogress.katcher.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ProcessReportUseCase
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.error.data.ErrorGroupViewedRepositoryImpl
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.data.ReportRepositoryImpl
import ru.workinprogress.feature.symbolication.SymbolicationService
import ru.workinprogress.feature.symbolication.data.SymbolMapRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.ErrorGroupCrudRepositoryImpl
import ru.workinprogress.katcher.db.SymbolMapCrudRepositoryImpl
import ru.workinprogress.retrace.MappingFileStorageOkio
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The use case with real repositories behind it. A repository test would prove the update
 * statement works; only this proves anybody calls it.
 */
class ReportProcessingTest : RepositoryTest() {
    private lateinit var useCase: ProcessReportUseCase
    private lateinit var groupRepository: ErrorGroupRepository
    private lateinit var appRepository: AppRepository
    private lateinit var reportRepository: ReportRepository

    private var appId = 0

    private val stacktrace =
        """
        java.lang.IllegalStateException: account not found
        	at com.acme.billing.AccountRepository.load(AccountRepository.kt:64)
        	at com.acme.billing.AccountService.charge(AccountService.kt:88)
        	at java.base/java.lang.Thread.run(Thread.java:1583)
        """.trimIndent()

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()

            appRepository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            groupRepository = ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl)
            reportRepository = ReportRepositoryImpl(db)

            useCase =
                ProcessReportUseCase(
                    symbolicationService =
                        SymbolicationService(
                            symbolMapRepository = SymbolMapRepositoryImpl(db, SymbolMapCrudRepositoryImpl),
                            fileStorage = MappingFileStorageOkio,
                            strategies = emptyMap(),
                        ),
                    errorGroupRepository = groupRepository,
                    reportRepository = reportRepository,
                    visitedRepository = ErrorGroupViewedRepositoryImpl(db),
                )

            appId = appRepository.create("billing", AppType.JVM).id
        }

    @Test
    fun `a new group is stored with a composed title`() =
        runTest {
            useCase.process(report(release = "1.4.2"), appId)

            val group = groupRepository.findByFingerprint(appId, fingerprint())
            assertNotNull(group)
            assertEquals("IllegalStateException", group.exceptionType)
            assertEquals("account not found", group.message)
            assertEquals("AccountRepository.kt:64", group.location)
        }

    @Test
    fun `a report on a resolved group reopens it and records the release it came back in`() =
        runTest {
            useCase.process(report(release = "1.4.1"), appId)
            val group = assertNotNull(groupRepository.findByFingerprint(appId, fingerprint()))
            groupRepository.resolve(group.id)
            assertTrue(assertNotNull(groupRepository.findById(group.id)).resolved)

            useCase.process(report(release = "1.4.2"), appId)

            val reopened = assertNotNull(groupRepository.findById(group.id))
            assertFalse(reopened.resolved, "a bug that came back is not fixed")
            assertTrue(reopened.regressed)
            assertEquals("1.4.2", reopened.regressedRelease)
            assertEquals(2, reopened.occurrences)
        }

    @Test
    fun `a group nobody resolved is not marked as a regression`() =
        runTest {
            useCase.process(report(release = "1.4.1"), appId)
            useCase.process(report(release = "1.4.2"), appId)

            val group = assertNotNull(groupRepository.findByFingerprint(appId, fingerprint()))
            assertNull(group.regressedAt)
            assertFalse(group.regressed)
        }

    @Test
    fun `activity reports the day buckets and the release range of a group`() =
        runTest {
            useCase.process(report(release = "1.4.1"), appId)
            useCase.process(report(release = "1.4.2"), appId)

            val group = assertNotNull(groupRepository.findByFingerprint(appId, fingerprint()))
            val activity = reportRepository.activity(listOf(group.id), currentMillis(), 7).getValue(group.id)

            assertEquals(7, activity.dailyCrashes.size)
            assertEquals(2, activity.dailyCrashes.last(), "both reports arrived in the running day")
            assertEquals("production", activity.environment)
            assertEquals("1.4.1 – 1.4.2", activity.releases)
        }

    @Test
    fun `an environment that is not the same in every report is left unsaid`() =
        runTest {
            useCase.process(report(release = "1.4.2", environment = "production"), appId)
            useCase.process(report(release = "1.4.2", environment = "staging"), appId)

            val group = assertNotNull(groupRepository.findByFingerprint(appId, fingerprint()))
            val activity = reportRepository.activity(listOf(group.id), currentMillis(), 7).getValue(group.id)

            assertNull(activity.environment)
            assertEquals("1.4.2", activity.releases)
        }

    private fun fingerprint() = ProcessReportUseCase.generateFingerprint(stacktrace)

    @OptIn(ExperimentalTime::class)
    private fun currentMillis() = Clock.System.now().toEpochMilliseconds()

    private fun report(
        release: String,
        environment: String = "production",
    ) = CreateReportParams(
        appKey = "unused-here",
        message = "account not found",
        stacktrace = stacktrace,
        release = release,
        environment = environment,
    )
}
