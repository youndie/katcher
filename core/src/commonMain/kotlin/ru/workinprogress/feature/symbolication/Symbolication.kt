@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.symbolication

import ru.workinprogress.retrace.Retracer
import kotlin.time.ExperimentalTime

interface Symbolicator {
    val supportedType: MappingType

    suspend fun symbolicate(
        rawStacktrace: String,
        mappingFileContent: String,
    ): String
}

enum class MappingType {
    ANDROID_PROGUARD,
//    IOS_DSYM,
//    JS_SOURCEMAP,
}

class AndroidR8Symbolicator : Symbolicator {
    override val supportedType = MappingType.ANDROID_PROGUARD

    override suspend fun symbolicate(
        rawStacktrace: String,
        mappingFileContent: String,
    ): String {
        val retracer = Retracer(mappingFileContent)
        return rawStacktrace.lines().joinToString("\n") { retracer.retrace(it) }
    }
}

interface SymbolMapRepository {
    suspend fun find(
        appId: Int,
        buildUuid: String,
    ): SymbolMap?

    suspend fun save(symbolMap: SymbolMap): Long
}

interface FileStorage {
    suspend fun readText(filePath: String): String

    suspend fun write(
        path: String,
        fileBytes: ByteArray,
    )
}

data class SymbolMap(
    val id: Long = 0L,
    val appId: Int,
    val buildUuid: String,
    val type: MappingType,
    val filePath: String,
    val versionName: String? = null,
    val createdAt: Long,
)
