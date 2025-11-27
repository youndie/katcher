package ru.workinprogress.katcher

import ru.workinprogress.katcher.jvm.setupJvmUncaughtExceptionHandler

actual fun init() {
    setupJvmUncaughtExceptionHandler()
}
