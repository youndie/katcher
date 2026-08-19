package ru.workinprogress.feature.report

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.html.body
import kotlinx.html.span
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.report.ui.groupStatusFragment
import ru.workinprogress.feature.report.ui.reportDetailsPage
import ru.workinprogress.feature.report.ui.reportRow
import ru.workinprogress.feature.report.ui.reportsTableFragment
import ru.workinprogress.katcher.ui.toast

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

        val appId = params.parent.parent.parent.parent.appId
        val groupId = params.parent.parent.groupId

        if (params.fragment) {
            // Opened in place, inside the list it was clicked in.
            call.respondHtml {
                body { context(call) { reportRow(appId, groupId, report, expanded = params.expanded) } }
            }
        } else {
            call.respondHtml {
                context(call) { reportDetailsPage(appId = appId, groupId = groupId, report = report) }
            }
        }
    }

    post<AppsResource.AppId.Errors.GroupId.Resolve> { resource ->
        errorGroupRepository.resolve(resource.parent.groupId)
        respondStatus(
            appId = resource.parent.parent.parent.appId,
            groupId = resource.parent.groupId,
            errorGroupRepository = errorGroupRepository,
            notice = "Group #${resource.parent.groupId} resolved",
            undoable = true,
        )
    }

    post<AppsResource.AppId.Errors.GroupId.Reopen> { resource ->
        errorGroupRepository.reopen(resource.parent.groupId)
        respondStatus(
            appId = resource.parent.parent.parent.appId,
            groupId = resource.parent.groupId,
            errorGroupRepository = errorGroupRepository,
            notice = "Group #${resource.parent.groupId} reopened",
            undoable = false,
        )
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
    notice: String,
    undoable: Boolean,
) {
    val group = errorGroupRepository.findById(groupId) ?: return call.respond(HttpStatusCode.NotFound)

    call.respondHtml {
        body {
            context(call) {
                groupStatusFragment(appId, group)

                toast(notice) {
                    // Undo is the opposite action, not a stored history: reopening a group
                    // somebody resolved a second ago is the same call as reopening one
                    // resolved last week.
                    if (undoable) {
                        span(
                            classes =
                                "text-xs font-mono text-muted-foreground underline underline-offset-2 " +
                                    "cursor-pointer hover:text-foreground transition",
                        ) {
                            attributes.hx {
                                post =
                                    call.application.href(
                                        AppsResource.AppId.Errors.GroupId.Reopen(
                                            parent =
                                                AppsResource.AppId.Errors.GroupId(
                                                    appId = appId,
                                                    groupId = groupId,
                                                ),
                                        ),
                                    )
                                target = "#group-status"
                                swap = HxSwap.innerHtml
                            }
                            +"Undo"
                        }
                    }
                }
            }
        }
    }
}
