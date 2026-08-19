import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ru.workinprogress.katcher.module

private const val PORT = 8080
private const val HOST = "0.0.0.0"

/** Two minutes: longer than TIME_WAIT, shorter than anyone's patience. */
private const val BIND_ATTEMPTS = 60
private const val BIND_RETRY_MILLIS = 2000L

fun main() {
    runBlocking { awaitPort() }

    embeddedServer(CIO, port = PORT, host = HOST, module = Application::module).start(wait = true)
}

/**
 * Waits until the port can actually be bound, and only then starts the server.
 *
 * When a container restarts it keeps the pod's network namespace, and the connections left by
 * the dead process sit in TIME_WAIT holding the port. Ktor's CIO socket does not ask for
 * SO_REUSEADDR, so its bind fails for as long as they last — and it fails inside the acceptor
 * coroutine, where nothing can catch it: the process dies, kubelet restarts it, the port is
 * still held, and one crash becomes minutes of CrashLoopBackOff.
 *
 * Binding here first turns that into a wait. It is not a fix for whatever killed the previous
 * process; it is the difference between a blip and an outage.
 */
private suspend fun awaitPort() {
    // Default, not IO: on native IO is internal, and this selector lives for one bind test.
    val selector = SelectorManager(kotlinx.coroutines.Dispatchers.Default)

    repeat(BIND_ATTEMPTS) { attempt ->
        try {
            aSocket(selector).tcp().bind(InetSocketAddress(HOST, PORT)).close()
            if (attempt > 0) println("port $PORT free after ${attempt + 1} attempts")
            selector.close()
            return
        } catch (cause: Throwable) {
            if (cause::class.simpleName != "AddressAlreadyInUseException") throw cause
            println("port $PORT still held, waiting (${attempt + 1}/$BIND_ATTEMPTS)")
            delay(BIND_RETRY_MILLIS)
        }
    }

    selector.close()
    error("port $PORT was still in use after $BIND_ATTEMPTS attempts")
}
