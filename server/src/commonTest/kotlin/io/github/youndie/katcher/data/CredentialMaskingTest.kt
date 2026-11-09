package io.github.youndie.katcher.data

import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.youndie.katcher.db.AppsCrudRepositoryImpl
import io.github.youndie.katcher.db.ErrorGroupCrudRepositoryImpl
import io.github.youndie.katcher.db.SymbolMapCrudRepositoryImpl
import io.github.youndie.katcher.db.remaskStoredCredentials
import io.github.youndie.katcher.feature.app.AppType
import io.github.youndie.katcher.feature.app.data.AppRepositoryImpl
import io.github.youndie.katcher.feature.error.CreateErrorGroupParams
import io.github.youndie.katcher.feature.error.ErrorGroupRepository
import io.github.youndie.katcher.feature.error.ProcessReportUseCase
import io.github.youndie.katcher.feature.error.data.ErrorGroupRepositoryImpl
import io.github.youndie.katcher.feature.error.data.ErrorGroupViewedRepositoryImpl
import io.github.youndie.katcher.feature.report.Breadcrumb
import io.github.youndie.katcher.feature.report.CreateReportParams
import io.github.youndie.katcher.feature.report.CredentialMask
import io.github.youndie.katcher.feature.report.ReportRepository
import io.github.youndie.katcher.feature.report.data.ReportRepositoryImpl
import io.github.youndie.katcher.feature.report.maskCredentials
import io.github.youndie.katcher.feature.symbolication.SymbolicationService
import io.github.youndie.katcher.feature.symbolication.data.SymbolMapRepositoryImpl
import io.github.youndie.katcher.retrace.MappingFileStorageOkio
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** B-01: a credential a client printed into a crash is not stored, and one stored before is re-masked. */
class CredentialMaskingTest : RepositoryTest() {
    private lateinit var useCase: ProcessReportUseCase
    private lateinit var groupRepository: ErrorGroupRepository
    private lateinit var reportRepository: ReportRepository

    private var appId = 0

    private val botUrl = "https://api.example.org/bot123456:FAKE-test-secret/getUpdates"
    private val dbUrl = "postgres://user:FAKE-pass@db:5432/x"

    private val stacktrace =
        """
        java.io.IOException: GET $botUrl failed, fallback $dbUrl refused
        	at com.acme.notify.BotClient.send(BotClient.kt:41)
        	at com.acme.notify.Notifier.notify(Notifier.kt:17)
        	at java.base/java.lang.Thread.run(Thread.java:1583)
        """.trimIndent()

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

            appId = AppRepositoryImpl(db, AppsCrudRepositoryImpl).create("notify", AppType.JVM).id
        }

    @Test
    fun `a report from a client that did not mask is stored masked`() =
        runTest {
            useCase.process(report(), appId)

            val group = assertNotNull(groupRepository.findByFingerprint(appId, maskedFingerprint()))
            val stored = reportRepository.findByGroup(group.id, 1, 15).items.single()

            val everything =
                listOf(
                    group.title,
                    group.message,
                    stored.message,
                    stored.stacktrace,
                    stored.context,
                    stored.breadcrumbs,
                )
            everything.forEach { field ->
                assertFalse("FAKE-test-secret" in field.toString(), field.toString())
                assertFalse("FAKE-pass" in field.toString(), field.toString())
            }
            assertTrue("/getUpdates" in group.title && "db:5432" in group.title, group.title)
            assertTrue("/getUpdates" in group.message.orEmpty() && "db:5432" in group.message.orEmpty())
            assertTrue("/getUpdates" in stored.stacktrace && "db:5432" in stored.stacktrace)
        }

    @Test
    fun `a report masked by the client and one from an old client land in one group`() =
        runTest {
            useCase.process(report().maskCredentials(), appId)
            useCase.process(report(), appId)

            val group = assertNotNull(groupRepository.findByFingerprint(appId, maskedFingerprint()))
            assertEquals(2, group.occurrences)
        }

    @Test
    fun `stored credentials are re-masked and a second run changes nothing`() =
        runTest {
            val legacy = legacyGroupWithReport()

            remask()
            val once = snapshot(legacy)
            remask()
            val twice = snapshot(legacy)

            assertEquals(once, twice, "the second run rewrote something the first had already masked")
            assertFalse("FAKE-test-secret" in once, once)
            assertFalse("FAKE-pass" in once, once)
            assertTrue("/getUpdates" in once && "db:5432" in once, once)
        }

    @Test
    fun `a re-masked group still receives the next report of its failure`() =
        runTest {
            val legacy = legacyGroupWithReport()
            assertNotEquals(
                rawFingerprint(),
                maskedFingerprint(),
                "the case must be one where masking moves the fingerprint",
            )

            remask()
            useCase.process(report(), appId)

            val group = assertNotNull(groupRepository.findById(legacy))
            assertEquals(maskedFingerprint(), group.fingerprint)
            assertEquals(2, group.occurrences, "the next report opened a new group beside the re-masked one")
        }

    /** What ingest stored before it masked: the raw text, with the fingerprint of the raw text. */
    private suspend fun legacyGroupWithReport(): Long {
        val group =
            groupRepository.insert(
                CreateErrorGroupParams(
                    appId = appId,
                    fingerprint = rawFingerprint(),
                    title = stacktrace.lineSequence().take(2).joinToString("\n"),
                    exceptionType = "IOException",
                    message = stacktrace.lineSequence().first().substringAfter(": "),
                    location = "BotClient.kt:41",
                ),
            )
        reportRepository.insert(appId, group.id, report())
        groupRepository.updateOccurrences(group.id)
        return group.id
    }

    private suspend fun remask() = TransactionContext.withCurrent(db) { remaskStoredCredentials() }

    private suspend fun snapshot(groupId: Long): String {
        val group = assertNotNull(groupRepository.findById(groupId))
        val reports = reportRepository.findByGroup(groupId, 1, 15).items
        return "$group\n$reports"
    }

    private fun rawFingerprint() = ProcessReportUseCase.generateFingerprint(stacktrace)

    private fun maskedFingerprint() = ProcessReportUseCase.generateFingerprint(CredentialMask.mask(stacktrace))

    private fun report() =
        CreateReportParams(
            appKey = "unused-here",
            message = "GET $botUrl failed",
            stacktrace = stacktrace,
            context = mapOf("endpoint" to botUrl),
            breadcrumbs =
                listOf(
                    Breadcrumb(
                        timestamp = LocalDateTime(2026, 11, 9, 12, 0),
                        type = "http",
                        message = "GET $botUrl",
                        data = mapOf("db" to dbUrl),
                    ),
                ),
            release = "1.0.0",
            environment = "production",
        )
}
