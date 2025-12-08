package ru.workinprogress.katcher

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

const val DEFAULT_SECURITY_SCHEME = "auth-session"

fun Application.common() {
    install(Resources)

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            },
        )
    }

    install(StatusPages) {
        status(HttpStatusCode.Unauthorized) { call, _ ->
            if (call.request.headers["HX-Request"] == "true") {
                call.response.headers.append("HX-Redirect", "/login")
                call.respondText("")
            } else {
                call.respondRedirect("/login")
            }
        }
    }
}
