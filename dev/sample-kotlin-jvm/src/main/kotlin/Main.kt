import kotlinx.coroutines.coroutineScope
import ru.workinprogress.katcher.Katcher

suspend fun main() =
    coroutineScope {
        Katcher.start {
            appKey = "75cdbbdc2e6f44cab5f09ecac03a0af5"
            isDebug = true
            remoteHost = "http://localhost:8080"
            environment = "dev"
            release = "1.0.0"
        }

        Katcher.catch(RuntimeException("Test sad"))
    }
