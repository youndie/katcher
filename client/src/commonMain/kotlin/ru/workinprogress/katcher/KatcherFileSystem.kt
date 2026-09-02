package ru.workinprogress.katcher

import ru.workinprogress.feature.report.CreateReportParams

internal expect val fileSystem: KatcherFileSystem

internal interface KatcherFileSystem {
    /**
     * Проверяет, что отчёт есть куда положить, и бросает исключение, если нет. Вызывается из
     * [Katcher.start]: каталог, в который нельзя писать, обязан быть отказом при старте, а не
     * пойманным исключением в момент краша — там о нём узнаёт только println.
     */
    fun prepare()

    fun saveReport(params: CreateReportParams)

    fun getReports(): List<StoredReport>

    fun deleteReport(fileName: String)
}
