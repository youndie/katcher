package ru.workinprogress.feature.auth

import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.koin.ktor.ext.inject
import ru.workinprogress.feature.user.UserRepository

fun Route.authRoute() {
    val userRepository by inject<UserRepository>()

    authenticate("auth-oauth-keycloak") {
        get("/login") {
        }

        get("/login/callback") {
            val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
            val idToken = principal?.extraParameters?.get("id_token")

            if (idToken != null) {
                val payload = JWT.decode(idToken)
                val email = payload.getClaim("email").asString()
                val name = payload.getClaim("name").asString()
                val user = userRepository.findByEmail(email) ?: userRepository.create(email, name)

                call.sessions.set(UserSession(user.id, email))
                call.respondRedirect("/apps")
            } else {
                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            }
        }
    }

    get("/logout") {
        call.sessions.clear<UserSession>()
        call.respondRedirect("/")
    }
}
