package io.github.youndie.katcher.feature.error

import io.github.youndie.katcher.feature.report.CreateReportParams
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class ReportsQueueService(
    private val processReportUseCase: ProcessReportUseCase,
) {
    private val queue = Channel<Pair<CreateReportParams, Int>>(capacity = 1000)

    fun enqueueReport(
        params: CreateReportParams,
        appId: Int,
    ): Boolean = queue.trySend(params to appId).isSuccess

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "a report that cannot be processed must not stop the queue -- that is what this loop is for",
    )
    suspend fun work() {
        for ((params, appId) in queue) {
            // `try` and not `runCatching`: the cancellation was already rethrown, but from inside
            // `onFailure`, which is a shape neither a reader nor a rule can check at a glance.
            try {
                processReportUseCase.process(params, appId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // A report that cannot be processed must not stop the queue.
            }
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
