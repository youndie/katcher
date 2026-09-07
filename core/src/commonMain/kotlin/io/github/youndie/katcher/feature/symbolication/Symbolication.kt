@file:OptIn(ExperimentalTime::class)

package io.github.youndie.katcher.feature.symbolication

import io.github.youndie.katcher.retrace.MappingFileStorage
import io.github.youndie.katcher.retrace.Retracer
import io.github.youndie.katcher.retrace.create
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
