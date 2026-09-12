package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.CreateReportParams
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateFileSystemTest {
    private val fs = FileSystem.SYSTEM

    @Test
    fun `test the configured directory is where the report lands`() {
        val dir =
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "katcher-configured-${Random.nextLong().toULong().toString(16)}"

        try {
            val fileSystem = createFileSystem(dir.toString())
            fileSystem.prepare()
            fileSystem.saveReport(params())

            assertTrue(fs.exists(dir), "expected the configured directory to exist")
            assertEquals(
                "boom",
                fileSystem
                    .getReports()
                    .single()
                    .params.message,
            )
            assertTrue(
                fs
                    .list(dir)
                    .single()
                    .name
                    .startsWith("crash_"),
            )
        } finally {
            fs.deleteRecursively(dir, mustExist = false)
        }
    }

    // Каталог тут настоящий — тот, куда будет писать приложение без настройки. Поэтому убираем
    // за собой только свой файл: рядом могут лежать отчёты, которые ещё не уехали.
    @Test
    fun `test no configured directory means the platform default`() {
        val fileSystem = createFileSystem(null)
        fileSystem.prepare()
        val before = fileSystem.getReports().map { it.fileName }.toSet()

        fileSystem.saveReport(params())

        val stored = fileSystem.getReports().single { it.fileName !in before }
        assertTrue(fs.exists(defaultCacheDir() / stored.fileName), "expected the report in ${defaultCacheDir()}")

        fileSystem.deleteReport(stored.fileName)
    }

    @Test
    fun `test a relative configured directory is taken as written`() {
        val name = ".katcher-relative-${Random.nextLong().toULong().toString(16)}"

        try {
            val fileSystem = createFileSystem(name)
            fileSystem.prepare()
            fileSystem.saveReport(params())

            assertEquals(1, fs.list(name.toPath()).size)
        } finally {
            fs.deleteRecursively(name.toPath(), mustExist = false)
        }
    }

    private fun params() = CreateReportParams(appKey = "key", message = "boom", stacktrace = "stack")
}
