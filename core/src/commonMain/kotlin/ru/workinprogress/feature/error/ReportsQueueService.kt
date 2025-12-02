package ru.workinprogress.feature.error

import kotlinx.coroutines.channels.Channel
import ru.workinprogress.feature.report.CreateReportParams

class ReportsQueueService(
    private val processReportUseCase: ProcessReportUseCase,
) {
    private val queue = Channel<Pair<CreateReportParams, Int>>(capacity = 1000)

    fun enqueueReport(
        params: CreateReportParams,
        appId: Int,
    ): Boolean = queue.trySend(params to appId).isSuccess

    suspend fun work() {
        for ((params, appId) in queue) {
            runCatching { processReportUseCase.process(params, appId) }
        }
    }
}
