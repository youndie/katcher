package ru.workinprogress.katcher

import ru.workinprogress.feature.report.CreateReportParams
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalNativeApi::class)
class DefaultCacheDirTest {
    private val dir = defaultCacheDir()
    private val applePlatforms = setOf(OsFamily.IOS, OsFamily.MACOSX, OsFamily.TVOS, OsFamily.WATCHOS)

    // Один тест на обе ветки, а не «пропустить, если платформа не та»: пропуск на каждой платформе
    // прошёл бы вхолостую, и обе ветки могли бы быть сломаны при зелёном прогоне.
    @Test
    fun `test default cache dir follows the platform`() {
        if (Platform.osFamily in applePlatforms) {
            assertTrue(dir.isAbsolute, "expected an absolute path inside the sandbox, got $dir")
            assertTrue(
                dir.toString().endsWith("Library/Caches/katcher_cache"),
                "expected the caches directory, got $dir",
            )
        } else {
            assertEquals(".katcher_cache", dir.toString())
        }
    }

    // Каталог тут настоящий — тот, куда будет писать приложение. Поэтому убираем за собой
    // только свой файл: рядом могут лежать отчёты, которые ещё не уехали на сервер.
    @Test
    fun `test default cache dir is writable`() {
        val katcherFileSystem = NativeKatcherFileSystem()
        val before = katcherFileSystem.getReports().map { it.fileName }.toSet()

        katcherFileSystem.saveReport(
            CreateReportParams(
                appKey = "key",
                message = "boom",
                stacktrace = "stack",
                release = "1.0",
                environment = "test",
            ),
        )

        val stored = katcherFileSystem.getReports().single { it.fileName !in before }
        assertEquals("boom", stored.params.message)

        katcherFileSystem.deleteReport(stored.fileName)
    }
}
