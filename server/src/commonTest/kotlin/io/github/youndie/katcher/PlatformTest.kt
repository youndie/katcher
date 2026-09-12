package io.github.youndie.katcher

import io.github.youndie.sborka.probe.configuredProbeTarget
import io.github.youndie.sborka.probe.orFail
import io.github.youndie.sborka.probe.probePlatform
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/*
 * Does the platform under this target do what this server assumes?
 *
 * The assertions are `sborka:platform-probe`, and they go through ktor's own API rather than the
 * syscall under it: the resolution failure this portfolio paid for was in that API while every
 * syscall below it worked.
 *
 * ## WHICH NATIVE TARGET THIS PROBED IS DECIDED BY THE BUILD HOST
 *
 * `:server` resolves one native target from `os.name` and `os.arch`. So a green run on a Mac says
 * something about `macosArm64` and NOTHING about the `linuxX64` binary that ships.
 *
 * `parityCheck`'s summary line cannot tell you which: it lists Gradle targets, and this module's is
 * called `native`. The probe's own report can, and does — `platform probe on native LINUX X64`, read
 * out of `Platform.osFamily` and `cpuArchitecture` at run time rather than off a task name. That is
 * why the report is printed, and why this gate has to run where the image is built rather than
 * merely somewhere.
 *
 * ## WHAT IT DOES NOT COVER
 *
 * TLS, and not by oversight: this module has ktor's server and no client at all, so there is no
 * engine to hand the probe and no outbound call to assert. The report says so rather than staying
 * quiet about it.
 *
 * `runBlocking`, not `runTest`: the test dispatcher's clock is virtual, so a socket with a timeout
 * around it reports a timeout before it has done anything — a harness verdict that reads as a
 * platform one.
 */
class PlatformTest {
    @Test
    fun theBuildsProbeTargetReachesThisTest() {
        // The host arrives through an environment variable that `sborka.parity` sets and
        // `platform-probe` reads, and the two spell it in different builds. Without this assertion
        // the names could drift apart and every probe below would fall back to its default — a
        // lookup test that passes because it looked nowhere.
        val configured = assertNotNull(configuredProbeTarget(), "sborka.parity did not reach the test")
        assertEquals("localhost", configured.host)
    }

    @Test
    fun thePlatformDoesWhatThisServerAssumes() {
        runBlocking {
            SelectorManager(Dispatchers.Default).use { selector ->
                aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0)).use { listener ->
                    val port = (listener.localAddress as InetSocketAddress).port
                    val report = probePlatform(configuredProbeTarget()?.host ?: "localhost", port)
                    println(report)
                    report.orFail()
                }
            }
        }
    }
}
