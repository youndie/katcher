package ru.workinprogress.katcher

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.report.CreateReportParams

internal expect fun init()

object Katcher {
    var remoteHost = ""
    var appKey = ""
        set(value) {
            if (field.isNotEmpty()) {
                throw IllegalStateException("App Key can be set only once")
            }
            field = value
            if (field.isNotEmpty()) {
                init()
            }
        }

    var release = "Unspecified"
    var environment = "Dev"

    private val httpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                url {
                    protocol = URLProtocol.HTTPS
                    host = "$remoteHost/api"
                }
            }
        }

    suspend fun catch(throwable: Throwable) {
        if (appKey.isEmpty()) {
            println("""$LOGO App Key is empty""")

            return
        }

        if (remoteHost.isEmpty()) {
            println("""$LOGO Remote host is not set, skipping error sending""")
            return
        }

        println("""$LOGO KATCHED ${throwable.stackTraceToString()}""")

        try {
            val response: HttpResponse =
                httpClient.post("/reports") {
                    setBody(
                        CreateReportParams(
                            appKey = appKey,
                            message = throwable.message.toString(),
                            stacktrace = throwable.stackTraceToString(),
                            release = release,
                            environment = environment,
                        ),
                    )
                }
            if (response.status.isSuccess()) {
                println(""""$LOGO Error sent successfully""")
            } else {
                println(""""$LOGO Failed to send error: ${response.status}""")
            }
        } catch (e: Exception) {
            println(""""$LOGO Exception while sending error: ${e.message}""")
        }
    }

    private const val LOGO = """🚫"""
}
