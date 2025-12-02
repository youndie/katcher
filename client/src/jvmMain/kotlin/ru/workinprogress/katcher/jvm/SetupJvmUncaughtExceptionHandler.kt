package ru.workinprogress.katcher.jvm

import ru.workinprogress.katcher.Katcher

fun setupJvmUncaughtExceptionHandler() {
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
