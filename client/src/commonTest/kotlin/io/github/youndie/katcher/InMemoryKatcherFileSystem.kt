package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.CreateReportParams

/**
 * Хранилище в памяти: очередь проверяется на том, что она делает с отчётами, а не на том, как
 * платформа кладёт их на диск — это проверяют [NativeKatcherFileSystemTest] и его JVM-двойник.
 */
internal class InMemoryKatcherFileSystem(
    messages: List<String> = emptyList(),
) : KatcherFileSystem {
    private val stored = messages.mapIndexed { index, message -> report(index, message) }.toMutableList()

    var prepared: Boolean = false
        private set

    val fileNames: List<String>
        get() = stored.map { it.fileName }

    override fun prepare() {
        prepared = true
    }

    override fun saveReport(params: CreateReportParams) {
        stored += StoredReport("crash_${stored.size.toString().padStart(3, '0')}.json", params)
    }

    override fun getReports(): List<StoredReport> = stored.toList()

    override fun deleteReport(fileName: String) {
        stored.removeAll { it.fileName == fileName }
    }

    private fun report(
        index: Int,
        message: String,
    ) = StoredReport(
        fileName = "crash_${index.toString().padStart(3, '0')}.json",
        params = CreateReportParams(appKey = "key", message = message, stacktrace = "stack"),
    )
}
