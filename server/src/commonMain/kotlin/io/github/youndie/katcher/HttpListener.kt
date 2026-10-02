package io.github.youndie.katcher

import io.github.youndie.kore.config.ConfigKey
import io.github.youndie.kore.config.ConfigSchema
import io.github.youndie.kore.config.ConfigurationException
import io.github.youndie.kore.config.Environment
import io.github.youndie.kore.ktor.requireListenable

/** The port the server listens on. Fixed: the chart's `containerPort` and its three probes say 8080 too. */
const val HTTP_PORT = 8080

/** Every interface, as the engine binds it — and as the check below has to, to fail where the engine would. */
const val HTTP_HOST = "0.0.0.0"

/**
 * `SO_REUSEADDR` for the engine AND for the check before it — one value for both places.
 *
 * CIO defaults it to `false`, and the two targets read that differently: the JVM ignores it (the JDK
 * opens every server socket with the flag on), Kotlin/Native writes an explicit `0` into the socket.
 * So a native process restarted in place — a container restart keeps the pod's network namespace —
 * meets its predecessor's connections in `TIME_WAIT` and cannot bind (kore B-62). The flag has to be
 * on in **both** processes; the old `awaitPort()` loop waited out the `TIME_WAIT` instead, for up to
 * two minutes.
 *
 * The check has to bind with the flag the engine uses: `true` there and `false` on the engine lets
 * the check pass over a `TIME_WAIT` that the engine then cannot bind.
 */
const val REUSE_ADDRESS = true

/**
 * Why the server cannot listen on [port], as the one line to print before exiting — or `null` when it can.
 *
 * A busy port on Kotlin/Native is not an error message without this: CIO binds inside a coroutine of
 * its own after `start` has returned, the failure reaches that coroutine's root with no handler, and
 * the process ends with `SIGABRT` and some fifty lines of stack (kore B-59). kore binds the port once
 * beforehand, with the engine's address and flag, and answers a `ConfigurationException`. It narrows
 * the case rather than closing it — something can still take the port between this bind and the
 * engine's.
 *
 * **The schema is one synthetic key, and it never sees the process environment.** The check is a
 * method of kore's `Configuration`, and katcher reads its environment itself, unprefixed, with the port
 * not configurable at all. A schema reading the real environment would also refuse every undeclared
 * variable under its prefix — and the kubelet puts `KATCHER_PORT`, `KATCHER_SERVICE_HOST` and the rest
 * of the service-link variables into the pod for a service called `katcher`. So the schema is handed
 * the port this process listens on and nothing else, and the line printed names the port rather than
 * the key: no operator can set `KATCHER_HTTP_PORT`, because nothing reads it.
 */
fun listenRefusal(port: Int): String? {
    val key = ConfigKey.int("HTTP_PORT")
    return try {
        ConfigSchema("KATCHER", listOf(key))
            .read(Environment.of(mapOf("KATCHER_HTTP_PORT" to port.toString())))
            .requireListenable(key, host = HTTP_HOST, reuseAddress = REUSE_ADDRESS)
        null
    } catch (refusal: ConfigurationException) {
        "katcher cannot start: port " + refusal.problems.joinToString("; ") { it.message }
    }
}
