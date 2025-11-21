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

object Katcher {
    var remoteHost = ""
    var appKey = ""
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
            println("""$logo App Key is empty""")

            return
        }

        if (remoteHost.isEmpty()) {
            println("""$logo Remote host is not set, skipping error sending""")
            return
        }

        println("""$logo KATCHED ${throwable.stackTraceToString()}""")

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
                println(""""$logo Error sent successfully""")
            } else {
                println(""""$logo Failed to send error: ${response.status}""")
            }
        } catch (e: Exception) {
            println(""""$logo Exception while sending error: ${e.message}""")
        }
    }

    private const val logo = """🚫"""
}
