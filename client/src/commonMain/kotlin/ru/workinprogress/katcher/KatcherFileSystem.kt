package ru.workinprogress.katcher

import ru.workinprogress.feature.report.CreateReportParams

internal expect val fileSystem: KatcherFileSystem

internal interface KatcherFileSystem {
    fun saveReport(params: CreateReportParams)

    fun getReports(): List<StoredReport>

    fun deleteReport(fileName: String)
}
