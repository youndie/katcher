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
import ru.workinprogress.feature.app.ui.appsPage
import ru.workinprogress.feature.app.ui.onAppCreated
import ru.workinprogress.feature.error.ui.appErrorsPage

fun Route.appPagesRoute(appRepository: AppRepository) {
    get<AppsResource> {
        val apps = appRepository.findAll()
        call.respondHtml { context(call) { appsPage(apps) } }
    }
    get<AppsResource.Form> {
        call.respondHtml { body { context(call) { appCreateModal() } } }
    }
    post<AppsResource> {
        val params = call.receiveParameters()
        val name = params["name"] ?: error("name missing")
        val type = params["type"] ?: error("type missing")

        val created = appRepository.create(name, AppType.valueOf(type))
        call.respondHtml { body { context(call) { onAppCreated(created) } } }
    }
    get<AppsResource.AppId> { resource ->
        val app = appRepository.findById(resource.appId) ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respondHtml { context(call) { appErrorsPage(app) } }
    }
}
