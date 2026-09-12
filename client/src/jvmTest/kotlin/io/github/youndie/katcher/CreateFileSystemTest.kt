package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.CreateReportParams
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateFileSystemTest {
    @Test
    fun `test the configured directory is where the report lands`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "katcher-configured-${System.nanoTime()}")

        val fileSystem = createFileSystem(dir.absolutePath)
        fileSystem.prepare()
        fileSystem.saveReport(params())

        assertTrue(dir.isDirectory, "expected the configured directory to exist")
        assertEquals(
            "boom",
            fileSystem
                .getReports()
                .single()
                .params.message,
        )
        assertTrue(
            dir
                .listFiles()
                .orEmpty()
                .single()
                .name
                .startsWith("crash_"),
        )

        dir.deleteRecursively()
    }

    // Каталог тут настоящий — тот, куда будет писать приложение без настройки. Поэтому убираем
    // за собой только свой файл: рядом могут лежать отчёты, которые ещё не уехали.
    @Test
    fun `test no configured directory means the working directory`() {
        val default = File(System.getProperty("user.dir"), ".katcher_cache")
        val fileSystem = createFileSystem(null)
        fileSystem.prepare()
        val before = fileSystem.getReports().map { it.fileName }.toSet()

        fileSystem.saveReport(params())

        val stored = fileSystem.getReports().single { it.fileName !in before }
        assertTrue(File(default, stored.fileName).isFile, "expected the report in ${default.absolutePath}")

        fileSystem.deleteReport(stored.fileName)
    }

    private fun params() = CreateReportParams(appKey = "key", message = "boom", stacktrace = "stack")
}
