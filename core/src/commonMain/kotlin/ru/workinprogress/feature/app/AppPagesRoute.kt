package ru.workinprogress.feature.app

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.id
import ru.workinprogress.feature.app.ui.appCard
import ru.workinprogress.feature.app.ui.appCreateModal
import ru.workinprogress.feature.app.ui.appDeleteModal
import ru.workinprogress.feature.app.ui.appKeyRow
import ru.workinprogress.feature.app.ui.appMenu
import ru.workinprogress.feature.app.ui.appMenuButton
import ru.workinprogress.feature.app.ui.appReissueModal
import ru.workinprogress.feature.app.ui.appRenameModal
import ru.workinprogress.feature.app.ui.appsPage
import ru.workinprogress.feature.app.ui.appsSummaryFragment
import ru.workinprogress.feature.app.ui.onAppCreated
import ru.workinprogress.feature.auth.withUserId
import ru.workinprogress.feature.error.ui.appErrorsPage
import ru.workinprogress.katcher.ui.toast
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Route.appPagesRoute(
    appRepository: AppRepository,
    appOverviewRepository: AppOverviewRepository,
    appKeyRepository: AppKeyRepository,
) {
    fun now() = Clock.System.now().toEpochMilliseconds()

    get<AppsResource> {
        withUserId { userId ->
            val now = now()
            val apps = appRepository.findAll()
            val overviews = appOverviewRepository.overview(userId, now)
            val keys = appKeyRepository.listAll()

            call.respondHtml { context(call) { appsPage(apps, overviews, keys, now) } }
        }
    }

    get<AppsResource.Form> {
        call.respondHtml { body { context(call) { appCreateModal() } } }
    }

    post<AppsResource> {
        val params = call.receiveParameters()
        val name = params["name"] ?: error("name missing")
        val type = params["type"] ?: error("type missing")

        val created = appRepository.create(name, AppType.valueOf(type))
        val key = appKeyRepository.issue(created.id, now())

        call.respondHtml { body { context(call) { onAppCreated(created, listOf(key), now()) } } }
    }

    get<AppsResource.AppId> { resource ->
        withUserId { userId ->
            val app =
                appRepository.findById(resource.appId)
                    ?: return@withUserId call.respond(HttpStatusCode.NotFound)

            val now = now()
            val overview =
                appOverviewRepository.overview(userId, now)[app.id] ?: AppOverview.silent(app.id)

            call.respondHtml { context(call) { appErrorsPage(app, overview) } }
        }
    }

    get<AppsResource.AppId.Key> { resource ->
        val appId = resource.parent.appId
        val keys = appKeyRepository.listByApp(appId)
        val now = now()

        // The reveal is a fragment, not a flag: the key is only ever in a response somebody
        // asked for, and a reload puts the card back to masked.
        call.respondHtml {
            body { context(call) { appKeyRow(appId, keys, now, revealKey = true) } }
        }
    }

    get<AppsResource.AppId.Menu> { resource ->
        val appId = resource.parent.appId

        call.respondHtml {
            body {
                div {
                    id = "app-menu-$appId"
                    context(call) { if (resource.open) appMenu(appId) else appMenuButton(appId) }
                }
            }
        }
    }

    get<AppsResource.AppId.Rename> { resource ->
        val app =
            appRepository.findById(resource.parent.appId)
                ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respondHtml { body { context(call) { appRenameModal(app) } } }
    }

    post<AppsResource.AppId.Rename> { resource ->
        withUserId { userId ->
            val appId = resource.parent.appId
            val name = call.receiveParameters()["name"]?.trim().orEmpty()
            if (name.isEmpty()) return@withUserId call.respond(HttpStatusCode.BadRequest)

            appRepository.rename(appId, name)
            val app = appRepository.findById(appId) ?: return@withUserId call.respond(HttpStatusCode.NotFound)
            val now = now()
            val overview = appOverviewRepository.overview(userId, now)[appId] ?: AppOverview.silent(appId)
            val keys = appKeyRepository.listByApp(appId)

            call.respondHtml {
                body {
                    context(call) {
                        appCard(app = app, overview = overview, keys = keys, now = now)
                        toast("Renamed to $name")
                    }
                }
            }
        }
    }

    get<AppsResource.AppId.Reissue> { resource ->
        val appId = resource.parent.appId
        val app = appRepository.findById(appId) ?: return@get call.respond(HttpStatusCode.NotFound)
        val current = appKeyRepository.listByApp(appId).firstOrNull { key -> key.active }

        call.respondHtml { body { context(call) { appReissueModal(app, current, now()) } } }
    }

    post<AppsResource.AppId.Keys> { resource ->
        val appId = resource.parent.appId
        val now = now()
        appKeyRepository.issue(appId, now)
        val keys = appKeyRepository.listByApp(appId)

        call.respondHtml {
            body {
                context(call) {
                    // Revealed here, because this is the one moment the person is waiting for
                    // the value rather than for the card.
                    appKeyRow(appId, keys, now, revealKey = true)
                    toast("New key issued — the previous one still works")
                }
            }
        }
    }

    post<AppsResource.AppId.Keys.Revoke> { resource ->
        val appId = resource.parent.parent.appId
        val now = now()
        appKeyRepository.revoke(resource.keyId, now)
        val keys = appKeyRepository.listByApp(appId)

        call.respondHtml {
            body {
                context(call) {
                    appKeyRow(appId, keys, now, revealKey = false)
                    toast("Key revoked — reports with it are refused from now on")
                }
            }
        }
    }

    get<AppsResource.AppId.Delete> { resource ->
        val appId = resource.parent.appId
        val app = appRepository.findById(appId) ?: return@get call.respond(HttpStatusCode.NotFound)

        val contents = appRepository.contents(appId)

        call.respondHtml { body { context(call) { appDeleteModal(app, contents) } } }
    }

    delete<AppsResource.AppId> { resource ->
        withUserId { userId ->
            val appId = resource.appId
            val app = appRepository.findById(appId) ?: return@withUserId call.respond(HttpStatusCode.NotFound)

            appRepository.delete(appId)

            val now = now()
            val apps = appRepository.findAll()
            val overviews = appOverviewRepository.overview(userId, now)

            call.respondHtml {
                body {
                    context(call) {
                        // The card is replaced by nothing; the summary beside the logo is
                        // corrected out of band, because it counts what just changed.
                        appsSummaryFragment(apps, overviews)
                        toast("${app.name} deleted")
                    }
                }
            }
        }
    }
}
