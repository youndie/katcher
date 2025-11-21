package ru.workinprogress.feature.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.sessions
import ru.workinprogress.katcher.DEFAULT_SECURITY_SCHEME

suspend inline fun RoutingContext.withUserId(block: suspend (Int) -> Unit) {
    val identity =
        call.findUserSession()
            ?: throw BadRequestException("No user")

    block(identity.userId)
}

fun ApplicationCall.findUserSession(): UserSession? {
    val jwt = principal<UserSession>()
    if (jwt != null) return jwt

    val session = sessions.get(DEFAULT_SECURITY_SCHEME) as? UserSession
    if (session != null) return session

    return null
}
