package ru.workinprogress.feature.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.response.respond
import ru.workinprogress.feature.user.User
import ru.workinprogress.feature.user.UserRepository

const val HEADER_USER_AUTH = "header-user-auth"

const val X_AUTH_REQUEST_USER = "X-Auth-Request-User"
const val X_AUTH_REQUEST_EMAIL = "X-Auth-Request-Email"

fun AuthenticationConfig.headerUserIdAuth(
    userRepository: UserRepository,
    name: String = HEADER_USER_AUTH,
    configure: HeaderUserIdAuthProvider.Config.() -> Unit = {},
) {
    val provider = HeaderUserIdAuthProvider.Config(userRepository, name).apply(configure).build()
    register(provider)
}

class HeaderUserIdAuthProvider(
    private val userRepository: UserRepository,
    config: Config,
) : AuthenticationProvider(config) {
    class Config(
        private val userRepository: UserRepository,
        name: String?,
    ) : AuthenticationProvider.Config(name) {
        fun build() = HeaderUserIdAuthProvider(userRepository, this)
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val userName = call.request.headers[X_AUTH_REQUEST_USER]
        val email = call.request.headers[X_AUTH_REQUEST_EMAIL]

        if (userName == null || email == null) {
            context.challenge("MissingCred", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    "Missing credentials in headers: $X_AUTH_REQUEST_USER, $X_AUTH_REQUEST_EMAIL",
                )
                challenge.complete()
            }
            return
        }

        val user: User = userRepository.findByEmail(email) ?: userRepository.create(email, userName)
        context.principal(UserSession(user.id, user.email))
    }
}
