package ru.workinprogress.katcher

import ru.workinprogress.feature.report.CreateReportParams
import java.io.File
import java.util.UUID

/**
 * Хранение отчётов на файловой системе — общее для JVM и Android. Различаются они только каталогом:
 * на JVM он берётся из `user.dir`, на Android — из `Context.cacheDir`, и на Android узнать его
 * можно не раньше, чем появится контекст. Поэтому каталог приходит функцией, а не значением.
 */
internal open class FileKatcherFileSystem(
    private val cacheDir: () -> File,
    private val systemAttributes: () -> Map<String, String>,
) : KatcherFileSystem {
    override fun prepare() {
        val dir = cacheDir()
        if (!dir.isDirectory && !dir.mkdirs()) {
            error("cannot create the report directory at ${dir.absolutePath}")
        }
        if (!dir.canWrite()) {
            error("the report directory at ${dir.absolutePath} is not writable")
        }
    }

    override fun saveReport(params: CreateReportParams) {
        val dir = cacheDir()
        enforceLimit(dir)
        val fileName = "crash_${System.currentTimeMillis()}_${UUID.randomUUID()}.json"
        File(dir, fileName).writeText(Katcher.json.encodeToString(params))
    }

    override fun getReports(): List<StoredReport> =
        reportFiles(cacheDir())
            .mapNotNull { file ->
                try {
                    val params = Katcher.json.decodeFromString<CreateReportParams>(file.readText())
                    StoredReport(file.name, params.copy(context = systemAttributes() + params.context.orEmpty()))
                } catch (e: Exception) {
                    file.delete()
                    null
                }
            }.sortedBy { it.fileName }

    override fun deleteReport(fileName: String) {
        File(cacheDir(), fileName).delete()
    }

    private fun enforceLimit(dir: File) {
        val files = reportFiles(dir)

        if (files.size >= MAX_REPORTS) {
            files
                .sortedBy { it.lastModified() }
                .take(files.size - MAX_REPORTS + 1)
                .forEach { it.delete() }
        }
    }

    private fun reportFiles(dir: File): List<File> =
        dir.listFiles { _, name -> name.endsWith(".json") }?.toList().orEmpty()

    internal companion object {
        internal const val MAX_REPORTS = 50
    }
}
