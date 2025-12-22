@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.symbolication

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.resources.Resource
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.utils.io.toByteArray
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.retrace.Retracer
import kotlin.time.Clock
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

class SymbolicationService(
    private val symbolMapRepository: SymbolMapRepository,
    private val fileStorage: FileStorage,
    private val strategies: Map<MappingType, Symbolicator>,
) {
    suspend fun processCrash(
        appId: Int,
        buildUuid: String?,
        rawStacktrace: String,
    ): String {
        if (buildUuid == null) {
            println("Symbolication skipped: buildUuid is null")
            return rawStacktrace
        }
        println("Looking up symbol map for appId=$appId, buildUuid=$buildUuid")
        val mapMetadata =
            symbolMapRepository.find(appId, buildUuid)
                ?: run {
                    println("Symbol map not found for appId=$appId, buildUuid=$buildUuid")
                    return rawStacktrace
                }
        println("Found symbol map: type=${mapMetadata.type}, filePath=${mapMetadata.filePath}")
        val strategy =
            strategies[mapMetadata.type]
                ?: run {
                    println("No symbolication strategy found for type=${mapMetadata.type}")
                    return rawStacktrace
                }
        println("Reading mapping file: ${mapMetadata.filePath}")
        val content = fileStorage.readText(mapMetadata.filePath)
        println("Starting symbolication with ${content.length} bytes of mapping data")
        val result = strategy.symbolicate(rawStacktrace, content)
        println("Symbolication completed")
        println("Result: $result")
        return result
    }
}

@Resource("mappings")
class MappingsResource {
    @Resource("upload")
    class Upload(
        val parent: MappingsResource = MappingsResource(),
    )
}

fun Route.symbolMapRouting(
    appRepository: AppRepository,
    fileStorage: FileStorage,
    symbolMapRepository: SymbolMapRepository,
    serverConfig: ru.workinprogress.katcher.ServerConfig,
) {
    post<MappingsResource.Upload> {
        val multipart = call.receiveMultipart()
        var fileBytes: ByteArray? = null
        var buildUuid: String? = null
        var appKey: String? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    fileBytes = part.provider().toByteArray()
                }

                is PartData.FormItem -> {
                    part.name?.let { name ->
                        when (name) {
                            "appKey" -> appKey = part.value
                            "buildUuid" -> buildUuid = part.value
                        }
                    }
                }

                else -> {}
            }
        }

        if (appKey == null || buildUuid == null || fileBytes == null) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }

        val app = appRepository.findByApiKey(appKey) ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val path = "${serverConfig.sourceMapPath}/${app.id}/$buildUuid.txt"
        fileStorage.write(path, fileBytes)

        symbolMapRepository.save(
            SymbolMap(
                appId = app.id,
                buildUuid = buildUuid,
                type = MappingType.ANDROID_PROGUARD,
                filePath = path,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )

        call.respond(HttpStatusCode.Created)
    }
}
