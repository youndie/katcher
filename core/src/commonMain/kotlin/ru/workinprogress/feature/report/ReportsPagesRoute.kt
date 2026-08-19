package ru.workinprogress.feature.report

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.id
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.report.ui.groupStatusFragment
import ru.workinprogress.feature.report.ui.reportDetailsPage
import ru.workinprogress.feature.report.ui.reportsTableFragment

fun Route.reportsPagesRoute(
    errorGroupRepository: ErrorGroupRepository,
    reportRepository: ReportRepository,
) {
    get<AppsResource.AppId.Errors.GroupId.Reports.Paginated> { resource ->
        val data =
            reportRepository.findByGroup(
                groupId = resource.parent.parent.groupId,
                page = resource.page,
                pageSize = resource.pageSize,
            )

        call.respondHtml {
            context(call) {
                reportsTableFragment(
                    appId = resource.parent.parent.parent.parent.appId,
                    groupId = resource.parent.parent.groupId,
                    data = data,
                )
            }
        }
    }

    get<AppsResource.AppId.Errors.GroupId.Reports.ReportId> { params ->
        val report =
            reportRepository.getReportById(params.reportId)
                ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respondHtml {
            context(call) {
                reportDetailsPage(
                    appId = params.parent.parent.parent.parent.appId,
                    groupId = params.parent.parent.groupId,
                    report = report,
                )
            }
        }
    }

    post<AppsResource.AppId.Errors.GroupId.Resolve> { resource ->
        errorGroupRepository.resolve(resource.parent.groupId)
        respondStatus(resource.parent.parent.parent.appId, resource.parent.groupId, errorGroupRepository)
    }

    post<AppsResource.AppId.Errors.GroupId.Reopen> { resource ->
        errorGroupRepository.reopen(resource.parent.groupId)
        respondStatus(resource.parent.parent.parent.appId, resource.parent.groupId, errorGroupRepository)
    }
}

/**
 * Both actions answer with the same fragment, read back from the database rather than
 * assumed: the button that comes back is the one the stored state calls for.
 */
private suspend fun RoutingContext.respondStatus(
    appId: Int,
    groupId: Long,
    errorGroupRepository: ErrorGroupRepository,
) {
    val group = errorGroupRepository.findById(groupId) ?: return call.respond(HttpStatusCode.NotFound)

    call.respondHtml {
        body {
            context(call) { groupStatusFragment(appId, group) }
        }
    }
}
