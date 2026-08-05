package ru.workinprogress.katcher

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

const val DEFAULT_SECURITY_SCHEME = "auth-session"

/**
 * Whether the caller is a browser navigating to a page, as opposed to a machine client.
 * Browsers ask for `text/html`; API and MCP clients ask for JSON or an event stream.
 */
private fun ApplicationCall.wantsHtml(): Boolean =
    request.headers["Accept"]?.contains("text/html", ignoreCase = true) == true

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
            when {
                call.request.headers["HX-Request"] == "true" -> {
                    call.response.headers.append("HX-Redirect", "/login")
                    call.respondText("")
                }
                // Sending a browser to the login page only makes sense for a browser.
                // API and MCP clients need the status code itself: turning 401 into a
                // redirect makes them follow it and parse a login page as a response.
                call.wantsHtml() -> call.respondRedirect("/login")
                else -> call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}
