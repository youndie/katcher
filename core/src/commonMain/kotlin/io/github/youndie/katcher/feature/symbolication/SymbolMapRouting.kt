@file:OptIn(ExperimentalTime::class)

package io.github.youndie.katcher.feature.symbolication

import io.github.youndie.katcher.feature.app.AppKeyRepository
import io.github.youndie.katcher.retrace.MappingFileStorage
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import okio.Buffer
import okio.Sink
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "карта символов не разобралась — отчёт останется нерасшифрованным, но дойдёт",
)
fun Route.symbolMapRouting(
    appKeyRepository: AppKeyRepository,
    fileStorage: MappingFileStorage,
    symbolMapRepository: SymbolMapRepository,
    serverConfig: io.github.youndie.katcher.ServerConfig,
) {
    post<MappingsResource.Upload> {
        try {
            println("SymbolMapRouting - Received upload request")
            if (call.request.contentType().parameter("boundary") == null) {
                println("SymbolMapRouting - Missing boundary in request")
                return@post call.respond(HttpStatusCode.BadRequest, "Missing boundary")
            }
            // Ktor 3.6.0 parses multipart on non-JVM servers (KTOR-9784); before it the native
            // engine threw CannotTransformContentToTypeException here and this route carried its
            // own parser.
            val multipart = call.receiveMultipart()

            var buildUuid: String? = null
            var appKey: String? = null
            var fileProcessed = false
            var type: MappingType? = null

            while (true) {
                val part = multipart.readPart() ?: break
                try {
                    val name = part.name
                    println("SymbolMapRouting - Processing part: $name")

                    when (name) {
                        "appKey" -> {
                            appKey = part.text().trim()
                            println("SymbolMapRouting - Received appKey: $appKey")
                        }

                        "buildUuid" -> {
                            buildUuid = part.text().trim()
                            println("SymbolMapRouting - Received buildUuid: $buildUuid")
                        }

                        "type" -> {
                            type = MappingType.valueOf(part.text().trim())
                            println("SymbolMapRouting - Received type: $type")
                        }

                        "mappingFile", "file" -> {
                            println("SymbolMapRouting - Processing mapping file")
                            if (part !is PartData.FileItem) {
                                return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    "Part '$name' must be a file part (with a filename)",
                                )
                            }
                            if (appKey == null || buildUuid == null || type == null) {
                                println("SymbolMapRouting - Missing appKey or buildUuid or type before file")
                                return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    "Fields 'appKey', 'buildUuid', and 'type' must be sent before the file",
                                )
                            }

                            println("SymbolMapRouting - Looking up app with apiKey: $appKey")
                            val key =
                                appKeyRepository.findActiveByKey(appKey)
                                    ?: return@post call.respond(HttpStatusCode.Unauthorized).also {
                                        println("SymbolMapRouting - App not found or unauthorized")
                                    }

                            println("SymbolMapRouting - Found app with id: ${key.appId}")
                            val path = "${serverConfig.sourceMapPath}/${key.appId}/$buildUuid.txt"
                            println("SymbolMapRouting - Writing mapping file to: $path")

                            fileStorage.write(path) { fileSink ->
                                part.provider().copyTo(fileSink)
                            }
                            println("SymbolMapRouting - Mapping file written successfully")

                            println("SymbolMapRouting - Saving symbol map to repository")
                            @Suppress(
                                "ktlint:kapkan:wall-clock",
                                "сервер ставит время загрузки карты символов своими часами",
                            )
                            val id =
                                symbolMapRepository.save(
                                    SymbolMap(
                                        appId = key.appId,
                                        buildUuid = buildUuid,
                                        type = type,
                                        filePath = path,
                                        createdAt = Clock.System.now().toEpochMilliseconds(),
                                    ),
                                )
                            println("SymbolMapRouting - Symbol map saved successfully, id: $id")
                            fileProcessed = true
                        }

                        else -> {
                            println("SymbolMapRouting - Skipping unknown part: $name")
                        }
                    }
                } finally {
                    part.release()
                }
            }

            if (!fileProcessed) {
                println("SymbolMapRouting - No file was processed")
                return@post call.respond(HttpStatusCode.BadRequest, "No file uploaded")
            }

            println("SymbolMapRouting - Upload completed successfully")
            call.respond(HttpStatusCode.Created)
        } catch (e: Exception) {
            println("SymbolMapRouting - Upload failed with exception: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
        }
    }
}

/** A field the uploader sent as a file part still carries text; read it either way. */
private suspend fun PartData.text(): String =
    when (this) {
        is PartData.FormItem -> value
        is PartData.FileItem -> Buffer().also { provider().copyTo(it) }.readUtf8()
        else -> ""
    }

private suspend fun ByteReadChannel.copyTo(sink: Sink) {
    val chunk = ByteArray(8192)
    while (true) {
        val read = readAvailable(chunk)
        if (read == -1) break
        if (read > 0) sink.write(Buffer().write(chunk, 0, read), read.toLong())
    }
}
