package ru.workinprogress.katcher

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import ru.workinprogress.feature.auth.authRoute
import ru.workinprogress.feature.report.reportRoute

fun Application.configureRouting() =
    routing {
        staticResources("/static", "static")

        pagesRoute()
        authRoute()

        route("/api") {
            reportRoute(get(), get())
        }
    }
