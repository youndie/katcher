package ru.workinprogress.katcher

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
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

    install(CORS) {
        allowHost("katcher.kotlin.website")
        allowHost("*.kotlin.website")
        allowHost("localhost:3000") // frontend origin
        allowCredentials = true

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
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
