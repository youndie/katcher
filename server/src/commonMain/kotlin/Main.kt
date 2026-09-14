import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.youndie.katcher.KatcherProbes
import io.github.youndie.katcher.feature.error.ReportsQueueService
import io.github.youndie.katcher.getServerConfig
import io.github.youndie.katcher.initDb
import io.github.youndie.katcher.module
import io.github.youndie.kore.ktor.EngineDrain
import io.github.youndie.kore.lifecycle.AnnounceNotReady
import io.github.youndie.kore.lifecycle.ShutdownDeadlines
import io.github.youndie.kore.lifecycle.ShutdownParticipant
import io.github.youndie.kore.lifecycle.runUntilSignal
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

private const val PORT = 8080
private const val HOST = "0.0.0.0"

/** Two minutes: longer than TIME_WAIT, shorter than anyone's patience. */
private const val BIND_ATTEMPTS = 60
private const val BIND_RETRY_MILLIS = 2000L

/**
 * How the process stops, as numbers.
 *
 * They add up to 2 + 10 + 3×3 = 21 seconds against a 30-second termination grace period, which is
 * the chart's `terminationGracePeriodSeconds` and has to stay above this sum — a plan that does not
 * fit is a `SIGKILL` in the middle of a stage.
 *
 * **The pre-drain wait is shorter than kore's default five seconds, and that is a choice about
 * deploys rather than a measurement of this cluster.** The wait exists so a load balancer stops
 * sending traffic before the engine starts refusing it; kore measured endpoint propagation at 61 ms
 * on one node, and this deployment is a single replica with `strategy: Recreate` — there is no other
 * pod for traffic to move to, so every second here is a second of deploy downtime and nothing else.
 * Two seconds is 30× the measured propagation and 15× less than the default.
 */
private val DEADLINES =
    ShutdownDeadlines(
        preDrainWait = 2.seconds,
        drain = 10.seconds,
        releaseGroup = 3.seconds,
        gracePeriod = 30.seconds,
    )

fun main() {
    runBlocking { awaitPort() }

    val config = getServerConfig()

    // MIGRATIONS RUN HERE, before anything serves and before the startup gate opens. Opening the
    // database in `main` rather than inside the module is what gives the release stage below a
    // handle on the pool: it has to be closed *after* the engine has drained, and a close wired to
    // `ApplicationStopping` runs before the drain on Kotlin/Native and after it on the JVM, from
    // identical source. That asymmetry is why kore is here.
    val db = initDb(config)

    val probes = KatcherProbes(db)

    val server =
        embeddedServer(
            CIO,
            configure = {
                connectors.add(
                    EngineConnectorBuilder().apply {
                        port = PORT
                        host = HOST
                    },
                )
                // The engine is given the same numbers the drain stage uses. Ktor's own default is
                // one second, which is shorter than a great many real requests.
                shutdownGracePeriod = DEADLINES.drain.inWholeMilliseconds
                shutdownTimeout = (DEADLINES.drain + 5.seconds).inWholeMilliseconds
            },
            module = { module(db, config, probes) },
        )

    // NOT `wait = true`. The main thread has to reach the await below, or the signal arrives at a
    // process that has no sequence to run and is killed at the end of the grace period instead.
    server.start(wait = false)

    val checksScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    probes.start(checksScope)
    probes.startup.markStarted()

    val queue = runBlocking { server.application.dependencies.resolve<ReportsQueueService>() }

    runBlocking {
        runUntilSignal(
            DEADLINES,
            // Inside the callback, not on the line after the call: on the JVM this function
            // returning means the shutdown hook has returned and the process is already on its way
            // out. Native carries on, which is exactly what makes it easy to write the racing
            // version and never see it.
            onFinished = { run -> println(run.transcript) },
        ) {
            announce(AnnounceNotReady(probes.readiness))
            drain(EngineDrain(server, DEADLINES.drain, DEADLINES.drain + 5.seconds))

            // After the drain: the queue holds reports that were accepted with `202` and not yet
            // written. Draining it here is the difference between a crash tracker that loses
            // crashes during a deploy and one that does not.
            consumer(queueParticipant(queue))

            // Last, because everything above it writes through this pool.
            pool(databaseParticipant(db))

            telemetry(
                participant("health checks") {
                    probes.stop()
                    checksScope.cancel()
                },
            )
        }
    }
}

private fun queueParticipant(queue: ReportsQueueService) = participant("report queue") { queue.drain() }

private fun databaseParticipant(db: ISQLite) = participant("sqlite pool") { db.close().getOrThrow() }

/**
 * A participant out of a name and a lambda.
 *
 * The parameters are `label` and `block` rather than `name` and `stop`: inside the object those two
 * names belong to the members being overridden, and `stop()` calling `stop` would be the function
 * calling itself.
 */
private fun participant(
    label: String,
    block: suspend () -> Unit,
): ShutdownParticipant =
    object : ShutdownParticipant {
        override val name: String = label

        override suspend fun stop() {
            block()
        }
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
@Suppress(
    "ktlint:kapkan:cancellation-swallowed",
    "the broad catch rethrows everything except AddressAlreadyInUse, a cancellation with it",
)
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
            // Everything that is not AddressAlreadyInUse leaves on the next line, a cancellation
            // included -- the rule reads the shape of the catch and cannot see a conditional rethrow.
            if (cause::class.simpleName != "AddressAlreadyInUseException") throw cause
            println("port $PORT still held, waiting (${attempt + 1}/$BIND_ATTEMPTS)")
            delay(BIND_RETRY_MILLIS)
        }
    }

    selector.close()
    error("port $PORT was still in use after $BIND_ATTEMPTS attempts")
}
