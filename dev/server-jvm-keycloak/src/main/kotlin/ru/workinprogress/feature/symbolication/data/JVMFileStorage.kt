package ru.workinprogress.feature.symbolication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.workinprogress.feature.symbolication.FileStorage
import java.io.File

class JVMFileStorage : FileStorage {
    override suspend fun readText(filePath: String): String =
        withContext(Dispatchers.IO) {
            File(filePath).readText()
        }

    override suspend fun write(
        path: String,
        fileBytes: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            File(path).apply {
                parentFile?.mkdirs() ?: return@withContext
                writeBytes(fileBytes)
            }
        }
    }
}
