package ru.workinprogress.katcher

import ru.workinprogress.feature.report.CreateReportParams
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileKatcherFileSystemTest {
    private val root: File = File(System.getProperty("java.io.tmpdir"), "katcher-test-${System.nanoTime()}")

    private fun fileSystemAt(dir: File) = FileKatcherFileSystem({ dir }, { mapOf("runtime.name" to "Test") })

    @Test
    fun `test prepare creates the directory`() {
        val dir = File(root, "reports")

        fileSystemAt(dir).prepare()

        assertTrue(dir.isDirectory)
        dir.deleteRecursively()
    }

    @Test
    fun `test prepare refuses a directory it cannot create`() {
        val occupied =
            File(root, "occupied").apply {
                parentFile.mkdirs()
                writeText("not a directory")
            }

        val failure = assertFailsWith<IllegalStateException> { fileSystemAt(File(occupied, "reports")).prepare() }

        assertTrue(failure.message.orEmpty().contains("reports"), "message should name the path: ${failure.message}")
        occupied.delete()
    }

    // Ровно та ситуация, из-за которой отчёты не уезжали на Android: каталог есть, писать нельзя.
    @Test
    fun `test prepare refuses a directory it cannot write to`() {
        val dir = File(root, "read-only").apply { mkdirs() }
        assumeWritableFlagWorks(dir)

        val failure = assertFailsWith<IllegalStateException> { fileSystemAt(dir).prepare() }

        assertTrue(failure.message.orEmpty().contains("not writable"), "message: ${failure.message}")
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    @Test
    fun `test a prepared directory takes a report and gives it back`() {
        val dir = File(root, "roundtrip")
        val fileSystem = fileSystemAt(dir)
        fileSystem.prepare()

        fileSystem.saveReport(
            CreateReportParams(appKey = "key", message = "boom", stacktrace = "stack"),
        )

        val stored = fileSystem.getReports().single()
        assertEquals("boom", stored.params.message)
        assertEquals("Test", stored.params.context?.get("runtime.name"))

        fileSystem.deleteReport(stored.fileName)
        assertEquals(emptyList(), fileSystem.getReports())
        dir.deleteRecursively()
    }

    // root пишет куда угодно: под ним запрет ниже не наступает, и тест утверждал бы неправду.
    private fun assumeWritableFlagWorks(dir: File) {
        dir.setWritable(false)
        if (dir.canWrite()) {
            dir.deleteRecursively()
            throw IllegalStateException("cannot make a directory read-only here — run the test as a non-root user")
        }
    }
}
