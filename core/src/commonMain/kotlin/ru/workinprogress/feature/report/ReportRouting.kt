package ru.workinprogress.feature.report

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.error.ProcessReportUseCase

fun Route.reportRoute(
    appRepository: AppRepository,
    processReportUseCase: ProcessReportUseCase,
) {
    post<ReportResource> {
        val params = call.receive<CreateReportParams>()
        appRepository.findByApiKey(params.appKey)?.let { app ->
            processReportUseCase.process(params, app.id)
            call.respond(HttpStatusCode.Created)
        }
    }
}
