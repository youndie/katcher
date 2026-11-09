package io.github.youndie.katcher.data

import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.youndie.katcher.db.AppsCrudRepositoryImpl
import io.github.youndie.katcher.db.ErrorGroupCrudRepositoryImpl
import io.github.youndie.katcher.db.SymbolMapCrudRepositoryImpl
import io.github.youndie.katcher.db.refingerprintGroups
import io.github.youndie.katcher.feature.app.AppType
import io.github.youndie.katcher.feature.app.data.AppRepositoryImpl
import io.github.youndie.katcher.feature.error.CreateErrorGroupParams
import io.github.youndie.katcher.feature.error.ErrorGroupRepository
import io.github.youndie.katcher.feature.error.ProcessReportUseCase
import io.github.youndie.katcher.feature.error.data.ErrorGroupRepositoryImpl
import io.github.youndie.katcher.feature.error.data.ErrorGroupViewedRepositoryImpl
import io.github.youndie.katcher.feature.report.CreateReportParams
import io.github.youndie.katcher.feature.report.ReportRepository
import io.github.youndie.katcher.feature.report.data.ReportRepositoryImpl
import io.github.youndie.katcher.feature.symbolication.SymbolicationService
import io.github.youndie.katcher.feature.symbolication.data.SymbolMapRepositoryImpl
import io.github.youndie.katcher.retrace.MappingFileStorageOkio
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** B-02: a fingerprint change is a migration — stored groups are re-keyed, and the ones that coincide merge. */
class RefingerprintGroupsTest : RepositoryTest() {
    private lateinit var useCase: ProcessReportUseCase
    private lateinit var groupRepository: ErrorGroupRepository
    private lateinit var reportRepository: ReportRepository

    private var appId = 0

    private fun badQuery(target: String) =
        """
        |io.ktor.server.plugins.BadRequestException: Url decode failed for query parameters of $target
        |	at io.ktor.server.request.PipelineRequestKt.encodeParameters(PipelineRequest.kt:132)
        |	at io.ktor.server.cio.CIOApplicationRequest.getQueryParameters(CIOApplicationRequest.kt:29)
        |	at io.ktor.server.engine.BaseApplicationCall.getParameters(BaseApplicationCall.kt:18)
        """.trimMargin()

    private val unrelated =
        """
        |java.lang.IllegalStateException: account not found
        |	at com.example.app.AccountRepository.load(AccountRepository.kt:41)
        """.trimMargin()

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()
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
            appId = AppRepositoryImpl(db, AppsCrudRepositoryImpl).create("haul-like", AppType.JVM).id
        }

    @Test
    fun `two groups the new fingerprint merges end as one with both groups' reports`() =
        runTest {
            val older = legacyGroup("legacy-search", badQuery("/search?q=%zz"), reports = 2)
            val newer = legacyGroup("legacy-items", badQuery("/api/v1/items/42?filter=%E0%A4"), reports = 3)
            groupRepository.resolve(older)
            val other = legacyGroup("legacy-account", unrelated, reports = 1)

            refingerprint()

            val survivor = assertNotNull(groupRepository.findById(older), "the older group is the one kept")
            assertNull(groupRepository.findById(newer), "the newer group was merged away")
            assertEquals(5, survivor.occurrences)
            assertEquals(5, reportRepository.findByGroup(older, 1, 15).items.size)
            assertEquals(ProcessReportUseCase.generateFingerprint(badQuery("/x?y=z")), survivor.fingerprint)
            assertFalse(survivor.resolved, "one of the two was never resolved, so the merged group is not")

            val untouched = assertNotNull(groupRepository.findById(other))
            assertEquals(ProcessReportUseCase.generateFingerprint(unrelated), untouched.fingerprint)
            assertEquals(1, untouched.occurrences)
        }

    @Test
    fun `a second run changes nothing`() =
        runTest {
            val older = legacyGroup("legacy-search", badQuery("/search?q=%zz"), reports = 2)
            legacyGroup("legacy-items", badQuery("/api/v1/items/42?filter=%E0%A4"), reports = 3)
            val other = legacyGroup("legacy-account", unrelated, reports = 1)

            refingerprint()
            val once = snapshot(older, other)
            refingerprint()

            assertEquals(once, snapshot(older, other))
        }

    @Test
    fun `the next report of a merged failure joins the merged group`() =
        runTest {
            val older = legacyGroup("legacy-search", badQuery("/search?q=%zz"), reports = 1)
            legacyGroup("legacy-items", badQuery("/api/v1/items/42?filter=%E0%A4"), reports = 1)

            refingerprint()
            useCase.process(report(badQuery("/?utm_source=%GG")), appId)

            assertEquals(3, assertNotNull(groupRepository.findById(older)).occurrences)
        }

    /** A group as an older katcher stored it: keyed by a fingerprint today's code does not produce. */
    private suspend fun legacyGroup(
        fingerprint: String,
        stacktrace: String,
        reports: Int,
    ): Long {
        val group =
            groupRepository.insert(
                CreateErrorGroupParams(
                    appId = appId,
                    fingerprint = fingerprint,
                    title = stacktrace.lineSequence().take(2).joinToString("\n"),
                    exceptionType = null,
                    message = null,
                    location = null,
                ),
            )
        repeat(reports) {
            reportRepository.insert(appId, group.id, report(stacktrace))
            groupRepository.updateOccurrences(group.id)
        }
        return group.id
    }

    private suspend fun refingerprint() = TransactionContext.withCurrent(db) { refingerprintGroups() }

    private suspend fun snapshot(vararg groupIds: Long): String {
        val lines = mutableListOf<String>()
        for (id in groupIds) {
            lines += "${groupRepository.findById(id)} ${reportRepository.findByGroup(id, 1, 15).items.map { it.id }}"
        }
        return lines.joinToString("\n")
    }

    private fun report(stacktrace: String) =
        CreateReportParams(
            appKey = "unused-here",
            message = stacktrace.lineSequence().first(),
            stacktrace = stacktrace,
            release = "v0.6.0",
            environment = "stage",
        )
}
