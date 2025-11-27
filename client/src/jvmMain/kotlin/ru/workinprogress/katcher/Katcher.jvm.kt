package ru.workinprogress.katcher

import ru.workinprogress.katcher.jvm.setupJvmUncaughtExceptionHandler

actual fun setupPlatformHandler() {
    setupJvmUncaughtExceptionHandler()
}

fun getSystemAttributes(): Map<String, String> {
    val runtime = Runtime.getRuntime()
    return mapOf(
        "device.os" to "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
        "device.arch" to System.getProperty("os.arch"),
        "runtime.name" to "Java",
        "runtime.version" to System.getProperty("java.version"),
        "app.memory_free" to "${runtime.freeMemory() / 1024 / 1024} MB",
        "app.thread" to Thread.currentThread().name,
    )
}
