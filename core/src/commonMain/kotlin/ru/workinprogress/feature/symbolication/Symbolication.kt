@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.symbolication

import ru.workinprogress.retrace.MappingFileStorage
import ru.workinprogress.retrace.Retracer
import ru.workinprogress.retrace.create
import kotlin.time.ExperimentalTime

interface Symbolicator {
    val supportedType: MappingType

    suspend fun symbolicate(
        rawStacktrace: String,
        mappingFilePath: String,
        fileStorage: MappingFileStorage,
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
        mappingFilePath: String,
        fileStorage: MappingFileStorage,
    ): String {
        val retracer = Retracer.create(rawStacktrace, mappingFilePath, fileStorage)

        return rawStacktrace.lines().joinToString("\n") { line ->
            retracer.retrace(line)
        }
    }
}

interface SymbolMapRepository {
    suspend fun find(
        appId: Int,
        buildUuid: String,
    ): SymbolMap?

    suspend fun save(symbolMap: SymbolMap): Long
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
