package ru.workinprogress.feature.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.auth.withUserId
import ru.workinprogress.feature.error.ui.errorsTableFragment
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.ui.errorGroupPage

fun Route.errorGroupPagesRoute(
    errorGroupRepository: ErrorGroupRepository,
    viewedRepository: ErrorGroupViewedRepository,
    reportRepository: ReportRepository,
) {
    get<AppsResource.AppId.Errors.Paginated> { resource ->
        withUserId { userId ->
            val data =
                errorGroupRepository.findByAppId(
                    appId = resource.parent.parent.appId,
                    page = resource.page,
                    pageSize = resource.pageSize,
                    sortBy = resource.sortBy,
                    sortOrder = resource.sortOrder,
                    userId = userId,
                )

            call.respondHtml {
                context(call) {
                    errorsTableFragment(resource.parent.parent.appId, data)
                }
            }
        }
    }

    get<AppsResource.AppId.Errors.GroupId> { resource ->
        withUserId { userId ->
            val group =
                errorGroupRepository.findById(resource.groupId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

            val stacktrace =
                reportRepository
                    .findByGroup(resource.groupId, 1, 1)
                    .items
                    .firstOrNull()
                    ?.stacktrace ?: group.title

            viewedRepository.updateVisitedAt(resource.groupId, userId)
            call.respondHtml {
                context(call) {
                    errorGroupPage(resource.parent.parent.appId, group, stacktrace)
                }
            }
        }
    }
}
