package ru.workinprogress.feature.error

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.auth.withUserId
import ru.workinprogress.feature.error.ui.errorsTableFragment
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.ui.errorGroupPage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The window the row trend is drawn over — the same seven days the app card counts. */
private const val TREND_DAYS = 7

@OptIn(ExperimentalTime::class)
fun Route.errorGroupPagesRoute(
    errorGroupRepository: ErrorGroupRepository,
    viewedRepository: ErrorGroupViewedRepository,
    reportRepository: ReportRepository,
) {
    get<AppsResource.AppId.Errors.Paginated> { resource ->
        withUserId { userId ->
            val now = Clock.System.now().toEpochMilliseconds()
            val data =
                errorGroupRepository.findByAppId(
                    appId = resource.parent.parent.appId,
                    page = resource.page,
                    pageSize = resource.pageSize,
                    sortBy = resource.sortBy,
                    sortOrder = resource.sortOrder,
                    userId = userId,
                )

            // One call for the page that was just read, not one per row.
            val activity =
                reportRepository.activity(
                    groupIds = data.items.map { it.errorGroup.id },
                    now = now,
                    days = TREND_DAYS,
                )

            call.respondHtml {
                context(call) {
                    errorsTableFragment(resource.parent.parent.appId, data, activity, now)
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

    get<AppsResource.AppId.Errors.GroupId.CrashJson> { resource ->
        withUserId {
            val groupId = resource.parent.groupId
            val group =
                errorGroupRepository.findById(groupId)
                    ?: return@withUserId call.respond(HttpStatusCode.NotFound)

            // Reports come back newest-first, so the head of the first page is the most
            // recent occurrence — the one whose stacktrace and release describe the
            // build the crash was last seen on.
            val latestReport = reportRepository.findByGroup(groupId, 1, 1).items.firstOrNull()

            when (val result = buildCrashExport(group, latestReport)) {
                is CrashExportResult.Rejected -> {
                    call.respond(HttpStatusCode.UnprocessableEntity, result.reason)
                }

                is CrashExportResult.Ok -> {
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment
                            .withParameter(ContentDisposition.Parameters.FileName, result.fileName)
                            .toString(),
                    )
                    // Pretty-printed on purpose: this file gets committed to a repository
                    // and read in a PR diff, so it should be legible rather than compact.
                    call.respondText(
                        crashExportJson.encodeToString(result.export),
                        ContentType.Application.Json,
                    )
                }
            }
        }
    }
}

private val crashExportJson = Json { prettyPrint = true }
