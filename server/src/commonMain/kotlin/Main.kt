import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.youndie.katcher.HTTP_HOST
import io.github.youndie.katcher.HTTP_PORT
import io.github.youndie.katcher.KatcherProbes
import io.github.youndie.katcher.REUSE_ADDRESS
import io.github.youndie.katcher.endProcess
import io.github.youndie.katcher.feature.error.ReportsQueueService
import io.github.youndie.katcher.getServerConfig
import io.github.youndie.katcher.initDb
import io.github.youndie.katcher.listenRefusal
import io.github.youndie.katcher.module
import io.github.youndie.kore.ktor.EngineDrain
import io.github.youndie.kore.ktor.startForKore
import io.github.youndie.kore.lifecycle.AnnounceNotReady
import io.github.youndie.kore.lifecycle.DrainGate
import io.github.youndie.kore.lifecycle.ShutdownDeadlines
import io.github.youndie.kore.lifecycle.ShutdownParticipant
import io.github.youndie.kore.lifecycle.runUntilSignal
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

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
    // A port something already listens on is one line and exit 1, not the engine's `SIGABRT` (kore
    // B-59). There is no wait for `TIME_WAIT` here any more: the engine binds with `SO_REUSEADDR`,
    // so a process restarted in place binds over its predecessor's closed connections at once.
    listenRefusal(HTTP_PORT)?.let { refusal ->
        println(refusal)
        endProcess(1)
    }

    val config = getServerConfig()

    // MIGRATIONS RUN HERE, before anything serves and before the startup gate opens. Opening the
    // database in `main` rather than inside the module is what gives the release stage below a
    // handle on the pool: it has to be closed *after* the engine has drained, and a close wired to
    // `ApplicationStopping` runs before the drain on Kotlin/Native and after it on the JVM, from
    // identical source. That asymmetry is why kore is here.
    val db = initDb(config)

    val probes = KatcherProbes(db)

    // ONE latch for the refusal and for the drain. The refusal used to read readiness, which falls
    // at the start of the announce — so it answered `503` to exactly the requests the announce waits
    // for (kore B-61). `EngineDrain` opens this as its first act; the module's refusal reads it.
    val draining = DrainGate()

    val server =
        embeddedServer(
            CIO,
            configure = {
                connectors.add(
                    EngineConnectorBuilder().apply {
                        port = HTTP_PORT
                        host = HTTP_HOST
                    },
                )
                // The engine is given the same numbers the drain stage uses. Ktor's own default is
                // one second, which is shorter than a great many real requests.
                shutdownGracePeriod = DEADLINES.drain.inWholeMilliseconds
                shutdownTimeout = (DEADLINES.drain + 5.seconds).inWholeMilliseconds
                // The same value the check above bound with — see `REUSE_ADDRESS` (kore B-62).
                reuseAddress = REUSE_ADDRESS
            },
            module = { module(db, config, probes, draining) },
        )

    // NOT `wait = true`. The main thread has to reach the await below, or the signal arrives at a
    // process that has no sequence to run and is killed at the end of the grace period instead.
    //
    // And not `start(wait = false)` either. On the JVM that leaves Ktor's own shutdown hook on, the
    // JVM runs hooks concurrently, and Ktor's stops the engine at the signal, in the middle of the
    // announce (kore#90). On Kotlin/Native Ktor's signal handler is armed between `start` and kore's,
    // and a SIGTERM in that window hangs the process (kore B-63). `startForKore` starts without the
    // first and takes the signal for kore before the second; `EngineDrain` refuses to be built beside
    // a hook that is still on.
    server.startForKore()

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
            drain(EngineDrain(server, DEADLINES.drain, DEADLINES.drain + 5.seconds, draining))

            // After the drain: the queue holds reports that were accepted with `202` and not yet
            // written. Draining it here is the difference between a crash tracker that loses
            // crashes during a deploy and one that does not.
            consumer(queueParticipant(queue))

            // HERE, a stage BEFORE the pool, and not in `telemetry` where it used to be. The check is
            // a `SELECT 1` through the pool released in the next stage, so stopping it after that
            // stage let a pass run against a closed pool. kore orders stages, not the participants
            // inside one — this and the queue run concurrently, which is fine: neither needs the
            // other, both need the pool. Nobody reads readiness by now; the engine has drained.
            consumer(
                participant("health checks") {
                    probes.stop()
                    checksScope.cancel()
                },
            )

            // Last of what holds resources, because everything above it goes through this pool.
            // RELEASE_TELEMETRY stays empty and still reports: the metrik agent flushes itself on
            // `ApplicationStopping`, inside the drain, and nothing else here reports on the shutdown.
            pool(databaseParticipant(db))
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
