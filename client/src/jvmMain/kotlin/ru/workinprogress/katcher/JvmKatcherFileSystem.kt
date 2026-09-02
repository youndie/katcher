package ru.workinprogress.katcher

import java.io.File

internal actual val fileSystem: KatcherFileSystem = JvmKatcherFileSystem()

internal class JvmKatcherFileSystem : FileKatcherFileSystem(::jvmCacheDir, ::getSystemAttributes)

private fun jvmCacheDir(): File = File(System.getProperty("user.dir"), ".katcher_cache")
