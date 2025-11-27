package ru.workinprogress.katcher

import ru.workinprogress.feature.report.CreateReportParams

data class StoredReport(
    val fileName: String,
    val params: CreateReportParams,
)
