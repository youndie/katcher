package ru.workinprogress.katcher

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.getenv
import ru.workinprogress.feature.report.CreateReportParams
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlin.random.Random
import kotlin.time.Clock

internal actual val fileSystem: KatcherFileSystem = NativeKatcherFileSystem()

/**
 * На Apple рабочий каталог процесса — не то место, куда можно писать: у приложения на iOS это
 * read-only бандл, и запись отчёта молча превращается в исключение внутри обработчика краша.
 * Записываемый каталог внутри песочницы, переживающий перезапуск, — `$HOME/Library/Caches`.
 * На остальных платформах поведение прежнее: `.katcher_cache` рядом с рабочим каталогом.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
internal fun defaultCacheDir(): Path =
    when (Platform.osFamily) {
        OsFamily.IOS, OsFamily.MACOSX, OsFamily.TVOS, OsFamily.WATCHOS -> {
            val home = getenv("HOME")?.toKString()?.takeIf { it.isNotEmpty() }?.toPath()
            if (home != null) {
                home / "Library" / "Caches" / CACHE_DIR_NAME
            } else {
                FileSystem.SYSTEM_TEMPORARY_DIRECTORY / CACHE_DIR_NAME
            }
        }

        else -> {
            ".$CACHE_DIR_NAME".toPath()
        }
    }

private const val CACHE_DIR_NAME = "katcher_cache"

public class NativeKatcherFileSystem(
    private val cacheDir: Path = DEFAULT_CACHE_DIR,
    private val fs: FileSystem = FileSystem.SYSTEM,
) : KatcherFileSystem {
    override fun prepare() {
        fs.createDirectories(cacheDir)

        // Каталог создан — это ещё не значит, что в него пишут: путь может быть занят файлом,
        // смонтирован только на чтение, или лежать в песочнице чужого процесса. Спрашиваем записью.
        val probe = cacheDir / ".katcher_probe"
        try {
            fs.write(probe) { writeUtf8("katcher") }
        } finally {
            runCatching { fs.delete(probe, mustExist = false) }
        }
    }

    override fun saveReport(params: CreateReportParams) {
        fs.createDirectories(cacheDir)
        enforceLimit()

        val fileName = "crash_${Clock.System.now().toEpochMilliseconds()}_${Random.nextLong().toULong().toString(
            16,
        )}.json"
        fs.write(cacheDir / fileName) {
            writeUtf8(Katcher.json.encodeToString(params))
        }
    }

    override fun getReports(): List<StoredReport> =
        reportFiles()
            .mapNotNull { path ->
                try {
                    val content = fs.read(path) { readUtf8() }
                    val params = Katcher.json.decodeFromString<CreateReportParams>(content)
                    StoredReport(path.name, params.copy(context = getSystemAttributes() + params.context.orEmpty()))
                } catch (e: Exception) {
                    delete(path)
                    null
                }
            }.sortedBy { it.fileName }

    override fun deleteReport(fileName: String) {
        delete(cacheDir / fileName)
    }

    private fun enforceLimit() {
        val files = reportFiles()

        if (files.size >= MAX_REPORTS) {
            files
                .sortedBy { fs.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }
                .take(files.size - MAX_REPORTS + 1)
                .forEach { delete(it) }
        }
    }

    private fun reportFiles(): List<Path> =
        if (fs.exists(cacheDir)) {
            fs.list(cacheDir).filter { it.name.endsWith(".json") }
        } else {
            emptyList()
        }

    private fun delete(path: Path) {
        runCatching { fs.delete(path, mustExist = false) }
    }

    public companion object {
        internal const val MAX_REPORTS = 50

        private val DEFAULT_CACHE_DIR: Path get() = defaultCacheDir()
    }
}
