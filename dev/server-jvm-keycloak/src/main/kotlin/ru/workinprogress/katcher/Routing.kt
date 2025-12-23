package ru.workinprogress.katcher

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import ru.workinprogress.feature.auth.authRoute
import ru.workinprogress.feature.report.reportRoute
import ru.workinprogress.feature.symbolication.symbolMapRouting

fun Application.configureRouting() =
    routing {
        staticResources("/static", "static")

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
