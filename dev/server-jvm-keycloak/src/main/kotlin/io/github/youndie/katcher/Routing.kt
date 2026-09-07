package io.github.youndie.katcher

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import io.github.youndie.katcher.feature.auth.authRoute
import io.github.youndie.katcher.feature.report.reportRoute
import io.github.youndie.katcher.feature.symbolication.symbolMapRouting
import io.github.youndie.katcher.ui.Icons

fun Application.configureRouting() =
    routing {
        staticResources("/static", "static")

        // The native server answers this from ConfigureRouting; the dev server has to answer
        // it too, or the tab here quietly disagrees with the tab in production.
        get("/favicon.svg") {
            call.respondText(Icons.FAVICON_SVG, ContentType.Image.SVG)
        }

        pagesRoute()
        authRoute()

        route("api") {
            reportRoute(get(), get())
            symbolMapRouting(
                get(),
                get(),
                get(),
                ServerConfig(
                    sourceMapPath = "data/mappings",
                ),
            )
        }
    }
