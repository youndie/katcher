import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import ru.workinprogress.katcher.Katcher
import kotlin.time.Duration.Companion.milliseconds

suspend fun main() =
    coroutineScope {
        Katcher.start {
            appKey = "75cdbbdc2e6f44cab5f09ecac03a0af5"
            isDebug = true
            remoteHost = "http://localhost:8080"
            environment = "dev"
            release = "1.0.0"
        }

        Katcher.addBreadcrumb("User clicked button", type = "info", data = mapOf("button" to "login"))
        delay(300L.milliseconds)
        Katcher.addBreadcrumb("User navigated to login page", type = "navigation", data = mapOf("page" to "login"))
        delay(200L.milliseconds)
        Katcher.addBreadcrumb(
            "API request started",
            type = "http",
            data = mapOf("url" to "/api/auth", "method" to "POST"),
        )
        delay(400L.milliseconds)
        Katcher.addBreadcrumb("Form validation passed", type = "info", data = mapOf("field" to "email"))
        delay(150L.milliseconds)
        Katcher.addBreadcrumb("User session created", type = "info", data = mapOf("sessionId" to "abc123"))
        delay(250L.milliseconds)

        Katcher.catch(RuntimeException("Test sad"))
    }
