package ru.workinprogress.katcher

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.report.CreateReportParams

internal expect fun setupPlatformHandler()

data class KatcherConfig(
    var appKey: String = "",
    var remoteHost: String = "",
    var release: String = "Unspecified",
    var environment: String = "Dev",
    var isDebug: Boolean = false,
)

object Katcher {
    private var config: KatcherConfig = KatcherConfig()
    private const val LOGO = """📡"""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient by lazy {
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
                    takeFrom(config.remoteHost)
                }
            }
        }
    }

    fun start(configure: KatcherConfig.() -> Unit) {
        val newConfig = KatcherConfig().apply(configure)

        if (newConfig.appKey.isEmpty() || newConfig.remoteHost.isEmpty()) {
            println("$LOGO Configuration error: appKey and remoteHost are required.")
            return
        }

        config = newConfig

        setupPlatformHandler()

        if (config.isDebug) println("$LOGO Katcher initialized for ${config.environment}")
    }

    fun catch(throwable: Throwable) {
        if (config.appKey.isEmpty()) return

        if (config.isDebug) {
            println("$LOGO Caught: ${throwable.message}")
        }

        scope.launch {
            sendReport(throwable)
        }
    }

    private suspend fun sendReport(throwable: Throwable) {
        try {
            val response: HttpResponse =
                httpClient.post("api/reports") {
                    setBody(
                        CreateReportParams(
                            appKey = config.appKey,
                            message = throwable.message.toString(),
                            stacktrace = throwable.stackTraceToString(),
                            release = config.release,
                            environment = config.environment,
                        ),
                    )
                }

            if (config.isDebug) {
                if (response.status.isSuccess()) {
                    println("$LOGO Report sent successfully")
                } else {
                    println("$LOGO Failed to send: ${response.status}")
                }
            }
        } catch (e: Exception) {
            if (config.isDebug) println("$LOGO Transmission failed: ${e.message}")
        }
    }
}
