package ru.workinprogress.katcher

import ru.workinprogress.feature.report.CreateReportParams
import java.io.File

internal actual val fileSystem: KatcherFileSystem = JvmKatcherFileSystem()

class JvmKatcherFileSystem : KatcherFileSystem {
    override fun saveReport(params: CreateReportParams) {
        enforceLimit()
        val fileName = "crash_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.json"
        val file = File(cacheDir, fileName)
        val jsonString = Katcher.json.encodeToString(params)
        file.writeText(jsonString)
    }

    override fun getReports(): List<StoredReport> =
        cacheDir
            .listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    val content = file.readText()
                    val params = Katcher.json.decodeFromString<CreateReportParams>(content)
                    StoredReport(file.name, params.copy(context = getSystemAttributes() + params.context.orEmpty()))
                } catch (e: Exception) {
                    file.delete()
                    null
                }
            }?.sortedBy { it.fileName }
            ?: emptyList()

    override fun deleteReport(fileName: String) {
        File(cacheDir, fileName).delete()
    }

    private fun enforceLimit() {
        val files = cacheDir.listFiles { _, name -> name.endsWith(".json") } ?: return

        if (files.size >= MAX_REPORTS) {
            files.sortBy { it.lastModified() }
            val filesToDelete = files.take(files.size - MAX_REPORTS + 1)
            filesToDelete.forEach { it.delete() }
        }
    }

    companion object {
        private const val MAX_REPORTS = 50

        private val cacheDir =
            File(System.getProperty("user.dir"), ".katcher_cache").apply {
                mkdirs()
            }
    }
}
