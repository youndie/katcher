package ru.workinprogress.katcher

import kotlinx.atomicfu.atomic
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.native.ReportUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

@OptIn(ExperimentalNativeApi::class)
private val previousHook = atomic<ReportUnhandledExceptionHook?>(null)
private val hookInstalled = atomic(false)

@OptIn(ExperimentalNativeApi::class)
internal actual fun setupPlatformHandler() {
    if (!hookInstalled.compareAndSet(expect = false, update = true)) return

    previousHook.value =
        setUnhandledExceptionHook { throwable ->
            runCatching { Katcher.catch(throwable) }

            val previous = previousHook.value
            if (previous != null) {
                previous(throwable)
            } else {
                terminateWithUnhandledException(throwable)
            }
        }
}

@OptIn(ExperimentalNativeApi::class)
public fun getSystemAttributes(): Map<String, String> =
    mapOf(
        "device.os" to Platform.osFamily.name,
        "device.arch" to Platform.cpuArchitecture.name,
        "runtime.name" to "Kotlin/Native",
        "runtime.debug" to Platform.isDebugBinary.toString(),
    )
