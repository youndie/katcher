package io.github.youndie.katcher

import java.io.File

internal actual fun createFileSystem(cacheDir: String?): KatcherFileSystem = JvmKatcherFileSystem(cacheDir)

internal class JvmKatcherFileSystem(
    cacheDir: String? = null,
) : FileKatcherFileSystem({ cacheDir?.let(::File) ?: jvmCacheDir() }, ::getSystemAttributes)

private fun jvmCacheDir(): File = File(System.getProperty("user.dir"), ".katcher_cache")
