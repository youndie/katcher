package io.github.youndie.katcher.feature.error

import io.github.youndie.katcher.feature.report.CreateReportParams
import io.ktor.server.application.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Accepts reports from the route and processes them off the request thread.
 *
 * **The queue owns its worker, and that is what makes it stoppable in order.** It used to be
 * launched into a scope that `launchReportQueueService` created and then cancelled on
 * `ApplicationStopping` — which on Kotlin/Native runs *before* the engine drains, the opposite of
 * the JVM, from identical source. So on the target katcher actually deploys, a `SIGTERM` cancelled
 * the processing of reports that had already been accepted with `202`, while the engine was still
 * taking in new ones. A crash reporter losing crashes at exactly the moment something is going
 * wrong is the worst version of that bug.
 *
 * [drain] is what replaces it: stop accepting, finish the backlog, and only then let the caller
 * close the database under it. It is called from the release stage of kore's shutdown sequence, so
 * "and only then" is a guarantee rather than an ordering that happens to hold.
 */
class ReportsQueueService(
    private val processReportUseCase: ProcessReportUseCase,
) {
    private val queue = Channel<Pair<CreateReportParams, Int>>(capacity = 1000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null

    /**
     * `false` once the queue is closed, which is also what it answers when the queue is full — the
     * route turns both into `503`, and during a shutdown that is the honest answer: this process
     * will not be processing it.
     */
    fun enqueueReport(
        params: CreateReportParams,
        appId: Int,
    ): Boolean = queue.trySend(params to appId).isSuccess

    /** Starts the worker. Idempotent — a second call is ignored rather than adding a second loop. */
    fun start() {
        if (worker != null) return
        worker = scope.launch { work() }
    }

    /**
     * Stops accepting, finishes what is already queued, and returns.
     *
     * No timeout here on purpose: the deadline belongs to the shutdown stage that calls this, which
     * is where it can be stated once against the process's whole budget rather than guessed here.
     */
    suspend fun drain() {
        queue.close()
        worker?.join()
    }

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
    service.start()
}
