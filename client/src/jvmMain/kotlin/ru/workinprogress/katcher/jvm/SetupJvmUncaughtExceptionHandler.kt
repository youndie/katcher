package ru.workinprogress.katcher.jvm

import ru.workinprogress.katcher.Katcher

fun setupJvmUncaughtExceptionHandler() {
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        Katcher.catch(throwable)

        try {
            Thread.sleep(500)
        } catch (e: InterruptedException) {
        }
        currentHandler?.uncaughtException(thread, throwable)
    }
}
