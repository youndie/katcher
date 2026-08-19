package ru.workinprogress.feature.report

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import ru.workinprogress.feature.app.AppKeyRepository
import ru.workinprogress.feature.error.ReportsQueueService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
