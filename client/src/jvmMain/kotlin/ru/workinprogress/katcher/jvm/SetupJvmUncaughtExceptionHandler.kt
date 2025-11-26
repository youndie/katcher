package ru.workinprogress.katcher.jvm

import kotlinx.coroutines.runBlocking
import ru.workinprogress.katcher.Katcher

fun setupJvmUncaughtExceptionHandler() {
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runBlocking { Katcher.catch(throwable) }

        currentHandler?.uncaughtException(thread, throwable)
            ?: System.err.println("Uncaught exception: $throwable")
    }
}
