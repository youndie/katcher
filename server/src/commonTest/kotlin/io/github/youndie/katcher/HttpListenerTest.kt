package io.github.youndie.katcher

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A busy port is one line naming the port, not the engine's `SIGABRT` (kore B-59).
 *
 * The bind itself is kore's and is tested there. What kore cannot check for katcher is that `main`
 * asks about the port the engine is given and turns the answer into something an operator can read —
 * without the synthetic key, which no operator can set.
 *
 * The holding socket listens on `0.0.0.0`, as the engine does: on macOS `SO_REUSEADDR` lets a bind on
 * `0.0.0.0` through when only `127.0.0.1` is taken, and a test on the loopback address would pass there
 * for the wrong reason.
 *
 * There is no "a free port passes" control on purpose: on Kotlin/Native `ktor-network` hands a closed
 * server socket's descriptor to the selector thread and the port is released later, so such a control
 * would be flaky (kore caught it in its own CI). Every start of the server is that control — the check
 * stands in `main` before the engine.
 *
 * `runBlocking`, not `runTest`: the test dispatcher's clock is virtual, and this is a real socket.
 */
class HttpListenerTest {
    @Test
    fun aPortSomethingListensOnIsOneLineNamingThePort() {
        runBlocking {
            SelectorManager(Dispatchers.Default).use { selector ->
                aSocket(selector).tcp().bind(InetSocketAddress(HTTP_HOST, 0)).use { holder ->
                    val port = (holder.localAddress as InetSocketAddress).port

                    val refusal = assertNotNull(listenRefusal(port), "a held port $port was not refused")

                    assertTrue("port $port cannot be listened on" in refusal, refusal)
                    assertTrue("KATCHER_HTTP_PORT" !in refusal, refusal)
                    assertTrue('\n' !in refusal, refusal)
                }
            }
        }
    }
}
