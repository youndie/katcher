@file:OptIn(ExperimentalForeignApi::class)

import io.github.youndie.katcher.Katcher
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import platform.posix.getenv
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private fun env(
    name: String,
    default: String,
): String = getenv(name)?.toKString() ?: default

/**
 * Dogfooding app for the Kotlin/Native client. Modes:
 *  - catch (default) — Katcher.catch() on a handled exception, then wait for the uploader.
 *  - crash           — throw an uncaught exception so the unhandled exception hook fires.
 *  - flush           — start only, then hand the queue to Katcher.flush() and print its answer.
 *  - breadcrumbs     — no start; time Katcher.addBreadcrumb() and print the median ns per call (B-05).
 */
fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "catch"
    if (mode == "breadcrumbs") return benchmarkBreadcrumbs()
    val host = env("KATCHER_HOST", "http://host.docker.internal:8080")
    val appKey = env("KATCHER_APP_KEY", "")
    val waitSeconds = env("KATCHER_WAIT_SECONDS", "6").toIntOrNull() ?: 6
    val graceSeconds = env("KATCHER_GRACE_SECONDS", "5").toIntOrNull() ?: 5
    val cacheDirectory = env("KATCHER_CACHE_DIR", "")

    println("[sample] mode=$mode host=$host appKey=${appKey.take(8)}... wait=${waitSeconds}s grace=${graceSeconds}s")

    Katcher.start {
        this.appKey = appKey
        this.remoteHost = host
        isDebug = true
        environment = "docker-native"
        release = "native-sample-1.0.0"
        // Серверный бинарник: следующего запуска на этой файловой системе может не быть, поэтому
        // каталог указывает на смонтированный том, а фатальный краш ждёт отправки.
        cacheDir = cacheDirectory.takeIf { it.isNotEmpty() }
        crashUploadGrace = graceSeconds.seconds
    }

    Katcher.addBreadcrumb("process started", type = "info", data = mapOf("mode" to mode))
    Katcher.addBreadcrumb("config loaded", type = "info", data = mapOf("host" to host))

    when (mode) {
        "crash" -> {
            Katcher.addBreadcrumb("about to crash", type = "info")
            throw IllegalStateException("Uncaught crash from Kotlin/Native sample ($mode)")
        }

        "flush" -> {
            val delivered = runBlocking { Katcher.flush(graceSeconds.seconds) }
            println("[sample] flush mode: queue empty = $delivered")
        }

        else -> {
            Katcher.catch(
                RuntimeException("Handled crash from Kotlin/Native sample"),
                mapOf("mode" to mode),
            )
            runBlocking { delay(waitSeconds * 1000L) }
        }
    }

    println("[sample] done")
}

/**
 * The shape #78 measured: one loop per subject, batches calibrated to about 150 ms, an empty loop of
 * the same build as the floor, medians over the batches. Absolute nanoseconds belong to the machine;
 * the ratio to the floor is what carries over. Link it `release` to mean anything.
 */
private fun benchmarkBreadcrumbs() {
    val batches = env("KATCHER_BENCH_BATCHES", "200").toIntOrNull() ?: 200

    fun calibrate(op: () -> Unit): Int {
        var n = 1
        while (true) {
            val mark = TimeSource.Monotonic.markNow()
            repeat(n) { op() }
            if (mark.elapsedNow().inWholeMilliseconds >= 150) return n
            n *= 2
        }
    }

    fun median(op: () -> Unit): Double {
        val n = calibrate(op)
        val samples =
            List(batches) {
                val mark = TimeSource.Monotonic.markNow()
                repeat(n) { op() }
                mark.elapsedNow().inWholeNanoseconds.toDouble() / n
            }.sorted()
        return samples[samples.size / 2]
    }

    var sink = 0
    val floor = median { sink += 1 }
    val plain = median { Katcher.addBreadcrumb("tapped checkout") }
    val withData = median { Katcher.addBreadcrumb("tapped checkout", "ui", mapOf("screen" to "cart")) }
    println(
        "[bench] batches=$batches floor=${floor.ns()} addBreadcrumb=${plain.ns()} addBreadcrumb+data=${withData.ns()} (sink=$sink)",
    )
}

private fun Double.ns(): String = "${(this * 10).toLong() / 10.0} ns"
