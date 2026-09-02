package ru.workinprogress.katcher

import ru.workinprogress.katcher.jvm.setupJvmUncaughtExceptionHandler

public actual fun setupPlatformHandler() {
    setupJvmUncaughtExceptionHandler()
}
