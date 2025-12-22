package ru.workinprogress.feature.error

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import ru.workinprogress.feature.report.CreateReportParams
import kotlin.coroutines.cancellation.CancellationException

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
                .onFailure { if (it is CancellationException) throw it }
        }
    }
}

fun Application.launchReportQueueService(service: ReportsQueueService) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    monitor.subscribe(ApplicationStopping) {
        appScope.cancel()
    }

    appScope.launch {
        service.work()
    }
}
