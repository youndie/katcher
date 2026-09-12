package io.github.youndie.katcher.jvm

import io.github.youndie.katcher.Katcher

/**
 * Ожидание отправки живёт в `Katcher.catchFatal` и меряется `KatcherConfig.crashUploadGrace`.
 * Раньше здесь стоял `Thread.sleep(50)` — против `connectTimeoutMillis = 3_000` и двух ретраев это
 * было не отсрочкой, а лотереей: отчёт уезжал, если повезло, и молча оставался на диске, если нет.
 */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "это обработчик необработанных исключений: бросить отсюда — потерять и краш, и отчёт",
)
public fun setupJvmUncaughtExceptionHandler() {
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        runCatching { Katcher.catchFatal(e) }
        currentHandler?.uncaughtException(t, e)
    }
}
