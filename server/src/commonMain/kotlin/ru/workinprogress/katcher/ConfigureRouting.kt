package ru.workinprogress.katcher

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.appPagesRoute
import ru.workinprogress.feature.auth.HEADER_USER_AUTH
import ru.workinprogress.feature.error.errorGroupPagesRoute
import ru.workinprogress.feature.report.reportRoute
import ru.workinprogress.feature.report.reportsPagesRoute
import ru.workinprogress.feature.symbolication.FileStorage
import ru.workinprogress.feature.symbolication.SymbolMapRepository
import ru.workinprogress.feature.symbolication.symbolMapRouting
import ru.workinprogress.katcher.static.CSS

fun Application.configureRouting() =
    routing {
        get("/static/tailwind.css") {
            call.respondText(
                CSS,
                ContentType.Text.CSS,
            )
        }

        context(dependencies) {
            pagesRoute()
        }

        route("/api") {
            runBlocking {
                reportRoute(
                    dependencies.resolve(),
                    dependencies.resolve(),
                )

                symbolMapRouting(
                    dependencies.resolve(),
                    dependencies.resolve(),
                    dependencies.resolve(),
                    dependencies.resolve(),
                )
            }
        }
    }

context(dependencies: DependencyRegistry)
fun Route.pagesRoute() {
    authenticate(HEADER_USER_AUTH) {
        get("/") {
            call.respondRedirect("/apps")
        }
        runBlocking {
            appPagesRoute(dependencies.resolve())
            errorGroupPagesRoute(
                dependencies.resolve(),
                dependencies.resolve(),
                dependencies.resolve(),
            )
            reportsPagesRoute(
                dependencies.resolve(),
                dependencies.resolve(),
            )
        }
    }
}
