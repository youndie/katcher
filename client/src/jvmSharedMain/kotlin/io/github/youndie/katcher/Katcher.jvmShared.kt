package io.github.youndie.katcher

import io.github.youndie.katcher.jvm.setupJvmUncaughtExceptionHandler

public actual fun setupPlatformHandler() {
    setupJvmUncaughtExceptionHandler()
}
