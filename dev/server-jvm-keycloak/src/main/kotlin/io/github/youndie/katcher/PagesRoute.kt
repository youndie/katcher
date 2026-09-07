package io.github.youndie.katcher

import io.github.youndie.katcher.feature.app.appPagesRoute
import io.github.youndie.katcher.feature.error.errorGroupPagesRoute
import io.github.youndie.katcher.feature.report.reportsPagesRoute
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.get

fun Route.pagesRoute() {
    authenticate(DEFAULT_SECURITY_SCHEME) {
        get("/") {
            call.respondRedirect("/apps")
        }

        appPagesRoute(get(), get(), get())
        errorGroupPagesRoute(get(), get(), get())
        reportsPagesRoute(get(), get())
    }
}
