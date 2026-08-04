package ru.workinprogress.katcher

import kotlinx.datetime.LocalDateTime
import okio.FileSystem
import okio.Path
import ru.workinprogress.feature.report.Breadcrumb
import ru.workinprogress.feature.report.CreateReportParams
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeKatcherFileSystemTest {
    private val fs = FileSystem.SYSTEM
    private lateinit var cacheDir: Path
    private lateinit var katcherFileSystem: NativeKatcherFileSystem

    @BeforeTest
    fun setup() {
        cacheDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "katcher-test-${Random.nextLong().toULong().toString(16)}"
        katcherFileSystem = NativeKatcherFileSystem(cacheDir, fs)
    }

    @AfterTest
    fun tearDown() {
        fs.deleteRecursively(cacheDir, mustExist = false)
    }

    @Test
    fun `test saveReport creates cache directory and json file`() {
        assertFalse(fs.exists(cacheDir))

        katcherFileSystem.saveReport(params("boom"))

        val files = fs.list(cacheDir)
        assertEquals(1, files.size)
        assertTrue(files.single().name.startsWith("crash_"))
        assertTrue(files.single().name.endsWith(".json"))
    }

    @Test
    fun `test saved report is read back with all fields`() {
        val breadcrumb =
            Breadcrumb(
                timestamp = LocalDateTime(2026, 8, 4, 12, 30, 15),
                type = "navigation",
                message = "opened screen",
                data = mapOf("screen" to "main"),
            )

        katcherFileSystem.saveReport(params("boom", breadcrumbs = listOf(breadcrumb)))

        val stored = katcherFileSystem.getReports().single()
        assertEquals("key", stored.params.appKey)
        assertEquals("boom", stored.params.message)
        assertEquals("stack", stored.params.stacktrace)
        assertEquals("1.0", stored.params.release)
        assertEquals("test", stored.params.environment)
        assertEquals(listOf(breadcrumb), stored.params.breadcrumbs)
        assertEquals(stored.fileName, fs.list(cacheDir).single().name)
    }

    @Test
    fun `test getReports enriches context with system attributes`() {
        katcherFileSystem.saveReport(params("boom", context = mapOf("custom" to "value")))

        val context =
            katcherFileSystem
                .getReports()
                .single()
                .params.context
                .orEmpty()
        assertEquals("value", context["custom"])
        assertEquals("Kotlin/Native", context["runtime.name"])
        assertTrue(context.containsKey("device.os"))
        assertTrue(context.containsKey("device.arch"))
    }

    @Test
    fun `test report context wins over system attributes`() {
        katcherFileSystem.saveReport(params("boom", context = mapOf("runtime.name" to "Custom")))

        val context =
            katcherFileSystem
                .getReports()
                .single()
                .params.context
                .orEmpty()
        assertEquals("Custom", context["runtime.name"])
    }

    @Test
    fun `test getReports returns empty list when cache directory is missing`() {
        assertFalse(fs.exists(cacheDir))
        assertEquals(emptyList(), katcherFileSystem.getReports())
    }

    @Test
    fun `test getReports sorts reports by file name`() {
        writeRaw("crash_003.json", json(params("third")))
        writeRaw("crash_001.json", json(params("first")))
        writeRaw("crash_002.json", json(params("second")))

        val reports = katcherFileSystem.getReports()
        assertEquals(listOf("crash_001.json", "crash_002.json", "crash_003.json"), reports.map { it.fileName })
        assertEquals(listOf("first", "second", "third"), reports.map { it.params.message })
    }

    @Test
    fun `test corrupted report is skipped and deleted`() {
        writeRaw("crash_001.json", "definitely not json")
        writeRaw("crash_002.json", """{"appKey":"key"}""")
        writeRaw("crash_003.json", json(params("valid")))

        val reports = katcherFileSystem.getReports()
        assertEquals(listOf("valid"), reports.map { it.params.message })
        assertEquals(listOf("crash_003.json"), fs.list(cacheDir).map { it.name })
    }

    @Test
    fun `test non json files are ignored and kept`() {
        writeRaw("notes.txt", "not a report")
        katcherFileSystem.saveReport(params("boom"))

        assertEquals(1, katcherFileSystem.getReports().size)
        assertTrue(fs.exists(cacheDir / "notes.txt"))
    }

    @Test
    fun `test deleteReport removes only the given report`() {
        writeRaw("crash_001.json", json(params("first")))
        writeRaw("crash_002.json", json(params("second")))

        katcherFileSystem.deleteReport("crash_001.json")

        assertEquals(listOf("crash_002.json"), katcherFileSystem.getReports().map { it.fileName })
        assertFalse(fs.exists(cacheDir / "crash_001.json"))
    }

    @Test
    fun `test deleteReport of unknown file does not fail`() {
        katcherFileSystem.saveReport(params("boom"))

        katcherFileSystem.deleteReport("crash_does_not_exist.json")

        assertEquals(1, katcherFileSystem.getReports().size)
    }

    @Test
    fun `test saveReport enforces the report limit`() {
        repeat(NativeKatcherFileSystem.MAX_REPORTS + 5) { index ->
            katcherFileSystem.saveReport(params("boom $index"))
        }

        assertEquals(NativeKatcherFileSystem.MAX_REPORTS, fs.list(cacheDir).size)
        assertEquals(NativeKatcherFileSystem.MAX_REPORTS, katcherFileSystem.getReports().size)
    }

    @Test
    fun `test report limit does not evict foreign files`() {
        writeRaw("notes.txt", "not a report")
        repeat(NativeKatcherFileSystem.MAX_REPORTS + 1) { index ->
            katcherFileSystem.saveReport(params("boom $index"))
        }

        assertEquals(NativeKatcherFileSystem.MAX_REPORTS, katcherFileSystem.getReports().size)
        assertTrue(fs.exists(cacheDir / "notes.txt"))
    }

    @Test
    fun `test reports survive a new file system instance`() {
        katcherFileSystem.saveReport(params("boom"))

        val reopened = NativeKatcherFileSystem(cacheDir, fs)
        assertEquals(
            "boom",
            reopened
                .getReports()
                .single()
                .params.message,
        )

        reopened.deleteReport(reopened.getReports().single().fileName)
        assertNull(katcherFileSystem.getReports().firstOrNull())
    }

    private fun params(
        message: String,
        context: Map<String, String>? = null,
        breadcrumbs: List<Breadcrumb>? = null,
    ) = CreateReportParams(
        appKey = "key",
        message = message,
        stacktrace = "stack",
        context = context,
        breadcrumbs = breadcrumbs,
        release = "1.0",
        environment = "test",
    )

    private fun json(params: CreateReportParams) = Katcher.json.encodeToString(params)

    private fun writeRaw(
        name: String,
        content: String,
    ) {
        fs.createDirectories(cacheDir)
        fs.write(cacheDir / name) {
            writeUtf8(content)
        }
    }
}
