@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import platform.posix.getenv
import ru.workinprogress.katcher.Katcher

private fun env(
    name: String,
    default: String,
): String = getenv(name)?.toKString() ?: default

/**
 * Dogfooding app for the Kotlin/Native client. Modes:
 *  - catch (default) — Katcher.catch() on a handled exception, then wait for the uploader.
 *  - crash           — throw an uncaught exception so the unhandled exception hook fires.
 *  - flush           — start only, so the queue on disk gets drained.
 */
fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "catch"
    val host = env("KATCHER_HOST", "http://host.docker.internal:8080")
    val appKey = env("KATCHER_APP_KEY", "")
    val waitSeconds = env("KATCHER_WAIT_SECONDS", "6").toIntOrNull() ?: 6

    println("[sample] mode=$mode host=$host appKey=${appKey.take(8)}... wait=${waitSeconds}s")

    Katcher.start {
        this.appKey = appKey
        this.remoteHost = host
        isDebug = true
        environment = "docker-native"
        release = "native-sample-1.0.0"
    }

    Katcher.addBreadcrumb("process started", type = "info", data = mapOf("mode" to mode))
    Katcher.addBreadcrumb("config loaded", type = "info", data = mapOf("host" to host))

    when (mode) {
        "crash" -> {
            Katcher.addBreadcrumb("about to crash", type = "info")
            throw IllegalStateException("Uncaught crash from Kotlin/Native sample ($mode)")
        }

        "flush" -> {
            println("[sample] flush mode: only draining the on-disk queue")
        }

        else -> {
            Katcher.catch(
                RuntimeException("Handled crash from Kotlin/Native sample"),
                mapOf("mode" to mode),
            )
        }
    }

    runBlocking { delay(waitSeconds * 1000L) }
    println("[sample] done")
}
