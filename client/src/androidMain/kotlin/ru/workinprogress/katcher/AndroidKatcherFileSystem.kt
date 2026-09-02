package ru.workinprogress.katcher

import java.io.File

internal actual val fileSystem: KatcherFileSystem = AndroidKatcherFileSystem()

internal class AndroidKatcherFileSystem : FileKatcherFileSystem(::androidCacheDir, ::getSystemAttributes)

/**
 * `user.dir` на Android — это `/`, писать туда нельзя, и подменить свойство приложение тоже не может:
 * рантайм отвечает "Ignoring attempt to set property". Каталог берётся у контекста приложения,
 * который [KatcherInitProvider] кладёт в [KatcherContext] до `Application.onCreate`.
 */
private fun androidCacheDir(): File {
    val context = KatcherContext.applicationContext
    checkNotNull(context) {
        "no application Context: KatcherInitProvider did not run. " +
            "Call Katcher.installContext(context) before Katcher.start { }"
    }
    return File(context.cacheDir, "katcher_cache")
}
