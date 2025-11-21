package ru.workinprogress.feature.report

import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.id
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.report.ui.reportsTableFragment
import ru.workinprogress.feature.report.ui.resolvedFragment

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

    post<AppsResource.AppId.Errors.GroupId.Resolve> { resource ->
        errorGroupRepository.resolve(resource.parent.groupId)

        call.respondHtml {
            body {
                div(classes = "flex justify-between mb-8") {
                    id = "resolved-container"
                    resolvedFragment()
                }
            }
        }
    }
}
