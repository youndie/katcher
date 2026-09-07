package io.github.youndie.katcher

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
import io.github.youndie.katcher.feature.app.appPagesRoute
import io.github.youndie.katcher.feature.auth.HEADER_USER_AUTH
import io.github.youndie.katcher.feature.error.errorGroupPagesRoute
import io.github.youndie.katcher.feature.report.reportRoute
import io.github.youndie.katcher.feature.report.reportsPagesRoute
import io.github.youndie.katcher.feature.symbolication.symbolMapRouting
import io.github.youndie.katcher.static.CSS
import io.github.youndie.katcher.ui.Icons

fun Application.configureRouting() =
    routing {
        get("/static/tailwind.css") {
            call.respondText(
                CSS,
                ContentType.Text.CSS,
            )
        }

        get("/favicon.svg") {
            call.respondText(
                Icons.FAVICON_SVG,
                ContentType.Image.SVG,
            )
        }

        context(dependencies) {
            pagesRoute()
        }

        route("api") {
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
            appPagesRoute(dependencies.resolve(), dependencies.resolve(), dependencies.resolve())
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
