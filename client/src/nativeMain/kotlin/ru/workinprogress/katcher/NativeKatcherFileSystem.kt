package ru.workinprogress.katcher

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import ru.workinprogress.feature.report.CreateReportParams
import kotlin.random.Random
import kotlin.time.Clock

internal actual val fileSystem: KatcherFileSystem = NativeKatcherFileSystem()

class NativeKatcherFileSystem(
    private val cacheDir: Path = DEFAULT_CACHE_DIR,
    private val fs: FileSystem = FileSystem.SYSTEM,
) : KatcherFileSystem {
    override fun saveReport(params: CreateReportParams) {
        fs.createDirectories(cacheDir)
        enforceLimit()

        val fileName = "crash_${Clock.System.now().toEpochMilliseconds()}_${Random.nextLong().toULong().toString(16)}.json"
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

    companion object {
        internal const val MAX_REPORTS = 50

        private val DEFAULT_CACHE_DIR: Path = ".katcher_cache".toPath()
    }
}
