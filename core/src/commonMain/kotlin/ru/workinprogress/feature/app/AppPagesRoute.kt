package ru.workinprogress.feature.app

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.html.body
import ru.workinprogress.feature.app.ui.appCreateModal
import ru.workinprogress.feature.app.ui.appKeyRow
import ru.workinprogress.feature.app.ui.appsPage
import ru.workinprogress.feature.app.ui.onAppCreated
import ru.workinprogress.feature.auth.withUserId
import ru.workinprogress.feature.error.ui.appErrorsPage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Route.appPagesRoute(
    appRepository: AppRepository,
    appOverviewRepository: AppOverviewRepository,
) {
    get<AppsResource> {
        withUserId { userId ->
            val now = Clock.System.now().toEpochMilliseconds()
            val apps = appRepository.findAll()
            val overviews = appOverviewRepository.overview(userId, now)

            call.respondHtml { context(call) { appsPage(apps, overviews, now) } }
        }
    }

    get<AppsResource.AppId.Key> { resource ->
        val app =
            appRepository.findById(resource.parent.appId)
                ?: return@get call.respond(HttpStatusCode.NotFound)

        // The reveal is a fragment, not a flag: the key is only ever in a response the user
        // asked for, and a reload puts the card back to masked.
        call.respondHtml { body { context(call) { appKeyRow(app, revealKey = true) } } }
    }

    get<AppsResource.Form> {
        call.respondHtml { body { context(call) { appCreateModal() } } }
    }

    post<AppsResource> {
        val params = call.receiveParameters()
        val name = params["name"] ?: error("name missing")
        val type = params["type"] ?: error("type missing")

        val created = appRepository.create(name, AppType.valueOf(type))
        val now = Clock.System.now().toEpochMilliseconds()
        call.respondHtml { body { context(call) { onAppCreated(created, now) } } }
    }

    get<AppsResource.AppId> { resource ->
        withUserId { userId ->
            val app =
                appRepository.findById(resource.appId)
                    ?: return@withUserId call.respond(HttpStatusCode.NotFound)

            val now = Clock.System.now().toEpochMilliseconds()
            val overview =
                appOverviewRepository.overview(userId, now)[app.id] ?: AppOverview.silent(app.id)

            call.respondHtml { context(call) { appErrorsPage(app, overview) } }
        }
    }
}
