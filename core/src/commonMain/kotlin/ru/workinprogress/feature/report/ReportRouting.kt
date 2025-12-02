package ru.workinprogress.feature.report

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.error.ReportsQueueService

fun Route.reportRoute(
    appRepository: AppRepository,
    processReportUseCase: ReportsQueueService,
) {
    post<ReportResource> {
        val params = call.receive<CreateReportParams>()
        val app = appRepository.findByApiKey(params.appKey) ?: return@post call.respond(HttpStatusCode.Unauthorized)

        if (!processReportUseCase.enqueueReport(params, app.id)) {
            call.respond(HttpStatusCode.ServiceUnavailable)
        } else {
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
