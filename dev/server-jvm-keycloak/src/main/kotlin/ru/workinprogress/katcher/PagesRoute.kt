package ru.workinprogress.katcher

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.get
import ru.workinprogress.feature.app.appPagesRoute
import ru.workinprogress.feature.error.errorGroupPagesRoute
import ru.workinprogress.feature.report.reportsPagesRoute

fun Route.pagesRoute() {
    authenticate(DEFAULT_SECURITY_SCHEME) {
        get("/") {
            call.respondRedirect("/apps")
        }

        appPagesRoute(get())
        errorGroupPagesRoute(get(), get(), get())
        reportsPagesRoute(get(), get())
    }
}
