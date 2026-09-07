package io.github.youndie.katcher.jvm

import io.github.youndie.katcher.Katcher

@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "это обработчик необработанных исключений: бросить отсюда — потерять и краш, и отчёт",
)
public fun setupJvmUncaughtExceptionHandler() {
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        runCatching { Katcher.catch(e) }
        try {
            Thread.sleep(50)
        } catch (_: InterruptedException) {
        }
        currentHandler?.uncaughtException(t, e)
    }
}
