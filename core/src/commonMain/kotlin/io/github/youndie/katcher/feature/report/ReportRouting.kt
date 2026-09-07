package io.github.youndie.katcher.feature.report

import io.github.youndie.katcher.feature.app.AppKeyRepository
import io.github.youndie.katcher.feature.error.ReportsQueueService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Suppress(
    "ktlint:kapkan:wall-clock",
    "время приёма отчёта ставит сервер, а не отправитель",
)
@OptIn(ExperimentalTime::class)
fun Route.reportRoute(
    appKeyRepository: AppKeyRepository,
    processReportUseCase: ReportsQueueService,
) {
    post<ReportResource> {
        val params = call.receive<CreateReportParams>()
        val key =
            appKeyRepository.findActiveByKey(params.appKey)
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

        // Recorded before the report is even queued: this is what tells a person whether the
        // key they are about to revoke is still carrying traffic.
        appKeyRepository.markUsed(key.id, Clock.System.now().toEpochMilliseconds())

        if (!processReportUseCase.enqueueReport(params, key.appId)) {
            call.respond(HttpStatusCode.ServiceUnavailable)
        } else {
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
