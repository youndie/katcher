package ru.workinprogress.feature.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.oauth
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import ru.workinprogress.katcher.DEFAULT_SECURITY_SCHEME

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>(DEFAULT_SECURITY_SCHEME, SessionStorageMemory()) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = 7 * 24 * 60 * 60
            cookie.extensions["SameSite"] = "None"
            cookie.secure = true
        }
    }
}

fun Application.configureAuth() {
    install(Authentication) {
        oauth("auth-oauth-keycloak") {
            urlProvider = { "${System.getenv("HOSTNAME")}/login/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "keycloak",
                    authorizeUrl = "${System.getenv("KC_URL")}/realms/${System.getenv("KC_REALM")}/protocol/openid-connect/auth",
                    accessTokenUrl = "${System.getenv("KC_URL")}/realms/${System.getenv("KC_REALM")}/protocol/openid-connect/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("KC_CLIENT_ID"),
                    clientSecret = System.getenv("KC_SECRET"),
                    defaultScopes = listOf("openid", "email", "profile"),
                )
            }
            client = HttpClient(OkHttp)
        }
        session<UserSession>(DEFAULT_SECURITY_SCHEME) {
            validate { session -> session }
            challenge {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}
