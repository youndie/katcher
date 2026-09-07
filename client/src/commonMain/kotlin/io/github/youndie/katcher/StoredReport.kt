package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.CreateReportParams

public data class StoredReport(
    val fileName: String,
    val params: CreateReportParams,
)
