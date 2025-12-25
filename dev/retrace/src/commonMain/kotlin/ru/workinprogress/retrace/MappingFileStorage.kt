package ru.workinprogress.retrace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

interface MappingFileStorage {
    suspend fun read(
        path: String,
        readerAction: BufferedSource.() -> MappingStore,
    ): MappingStore

    suspend fun write(
        path: String,
        fileBytes: ByteArray,
    )
}

suspend fun Retracer.Companion.create(
    rawStacktrace: String,
    mappingFilePath: String,
    fileStorage: MappingFileStorage,
): Retracer {
    val classesToLoad = extractClassesFromStacktrace(rawStacktrace)

    val store =
        fileStorage.read(mappingFilePath) {
            StreamingMappingParser.parse(this, classesToLoad)
        }

    return Retracer(store)
}

object MappingFileStorageOkio : MappingFileStorage {
    override suspend fun read(
        path: String,
        readerAction: BufferedSource.() -> MappingStore,
    ): MappingStore =
        withContext(Dispatchers.IO) {
            val fileSystem = FileSystem.SYSTEM
            val okioPath = path.toPath()

            if (!fileSystem.exists(okioPath)) throw FileNotFoundException("File not found: $path")

            fileSystem.read(okioPath, readerAction)
        }

    override suspend fun write(
        path: String,
        fileBytes: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            val fileSystem = FileSystem.SYSTEM
            val okioPath = path.toPath()

            okioPath.parent?.let { parent ->
                if (!fileSystem.exists(parent)) {
                    fileSystem.createDirectories(parent)
                }
            }

            fileSystem.write(okioPath) {
                write(fileBytes)
            }
        }
    }
}
