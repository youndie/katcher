package io.github.youndie.katcher

import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.youndie.kore.health.HealthRegistry
import io.github.youndie.kore.health.LivenessGate
import io.github.youndie.kore.health.ReadinessGate
import io.github.youndie.kore.health.StartupGate
import io.github.youndie.kore.health.storeCheck
import kotlinx.coroutines.CoroutineScope

/**
 * The three questions a deployment asks this process, and the one dependency behind them.
 *
 * They are three because they fail for different reasons and are read by different machinery.
 * Before this, the chart asked **nothing**: it declared no probe at all, so a wedged process kept
 * receiving traffic and a starting one received it too early. One route answering `200` because the
 * process is alive would have been the other half of the same mistake.
 *
 * * **startup** — a latch. `markStarted()` is called once the migrations have run and the engine is
 *   serving; after that it never answers `503` again, because Kubernetes runs the startup probe only
 *   at startup and a later failure there would restart a pod that is trying to stop.
 * * **readiness** — the store check below, plus the shutdown latch. This is the one that is *meant*
 *   to fail: during the announce stage it goes false first, and traffic stops arriving before
 *   anything is closed.
 * * **liveness** — nothing declares this process wedged today. It is here because `/health` and
 *   `/health/live` have to answer something, and because a gate nobody trips is the honest state
 *   rather than a route that reports the opposite.
 *
 * The store check is a `SELECT 1`, not `pool.acquire()`. A pool hands back an idle connection while
 * the store behind it is gone; only a statement that reaches SQLite says the database answered.
 */
class KatcherProbes(
    db: ISQLite,
) {
    private val checks =
        HealthRegistry(
            listOf(
                storeCheck("sqlite") { db.fetchAll("SELECT 1;").getOrThrow() },
            ),
        )

    val startup: StartupGate = StartupGate()
    val readiness: ReadinessGate = ReadinessGate(checks)
    val liveness: LivenessGate = LivenessGate()

    /**
     * Starts the polling loop. **Nothing else does**: the registry caches results and refreshes them
     * on a loop of its own, so without this call `/health/ready` answers from checks that have never
     * run — `UNKNOWN`, for ever, which reads as a broken dependency rather than a missing call.
     */
    fun start(scope: CoroutineScope) {
        checks.start(scope)
    }

    fun stop() {
        checks.stop()
    }
}
