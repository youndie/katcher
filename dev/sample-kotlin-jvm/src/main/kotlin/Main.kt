import kotlinx.coroutines.runBlocking
import ru.workinprogress.katcher.Katcher
import java.lang.Thread.sleep

fun main() {
    Katcher.start {
        appKey = "d67fbe515e9f4063914722e412cd2da1"
        isDebug = true
        remoteHost = "http://localhost:8080"
        environment = "dev"
        release = "1.0.0"
    }

    Katcher.catch(RuntimeException("Test sad"))
    runBlocking { sleep(1000) }
}
