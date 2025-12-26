@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.symbolication

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import okio.Buffer
import okio.Sink
import okio.blackholeSink
import okio.use
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.retrace.MappingFileStorage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun Route.symbolMapRouting(
    appRepository: AppRepository,
    fileStorage: MappingFileStorage,
    symbolMapRepository: SymbolMapRepository,
    serverConfig: ru.workinprogress.katcher.ServerConfig,
) {
    post<MappingsResource.Upload> {
        try {
            println("SymbolMapRouting - Received upload request")
            val boundary =
                call.request.contentType().parameter("boundary")
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing boundary").also {
                        println("SymbolMapRouting - Missing boundary in request")
                    }

            println("SymbolMapRouting - Boundary: $boundary")
            val parser = MultipartStreamParser(call.receiveChannel(), boundary)

            var buildUuid: String? = null
            var appKey: String? = null
            var fileProcessed = false
            var type: MappingType? = null

            while (true) {
                val headers = parser.nextPartHeader() ?: break

                val contentDisposition = headers["Content-Disposition"] ?: ""
                val name = extractHeaderValue(contentDisposition, "name")
                println("SymbolMapRouting - Processing part: $name")

                when (name) {
                    "appKey" -> {
                        appKey = parser.readPartBodyString().trim()
                        println("SymbolMapRouting - Received appKey: $appKey")
                    }

                    "buildUuid" -> {
                        buildUuid = parser.readPartBodyString().trim()
                        println("SymbolMapRouting - Received buildUuid: $buildUuid")
                    }

                    "type" -> {
                        type = MappingType.valueOf(parser.readPartBodyString().trim())
                        println("SymbolMapRouting - Received type: $type")
                    }

                    "mappingFile", "file" -> {
                        println("SymbolMapRouting - Processing mapping file")
                        if (appKey == null || buildUuid == null || type == null) {
                            println("SymbolMapRouting - Missing appKey or buildUuid or type before file")
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                "Fields 'appKey', 'buildUuid', and 'type' must be sent before the file",
                            )
                        }

                        println("SymbolMapRouting - Looking up app with apiKey: $appKey")
                        val app =
                            appRepository.findByApiKey(appKey)
                                ?: return@post call.respond(HttpStatusCode.Unauthorized).also {
                                    println("SymbolMapRouting - App not found or unauthorized")
                                }

                        println("SymbolMapRouting - Found app with id: ${app.id}")
                        val path = "${serverConfig.sourceMapPath}/${app.id}/$buildUuid.txt"
                        println("SymbolMapRouting - Writing mapping file to: $path")

                        fileStorage.write(path) { fileSink ->
                            parser.streamPartBody(fileSink)
                        }
                        println("SymbolMapRouting - Mapping file written successfully")

                        println("SymbolMapRouting - Saving symbol map to repository")
                        val id =
                            symbolMapRepository.save(
                                SymbolMap(
                                    appId = app.id,
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
                        parser.skipPartBody()
                    }
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

// Workaround https://youtrack.jetbrains.com/issue/KTOR-7361/CIO-native-receiveMultipart-throw-CannotTransformContentToTypeException
class MultipartStreamParser(
    private val channel: ByteReadChannel,
    private val boundary: String,
) {
    private val boundaryBytes = "--$boundary".encodeToByteArray()
    private val partDelimiter = "\r\n--$boundary".encodeToByteArray()
    private var buffer = ByteArray(0)
    private var isFinished = false

    suspend fun nextPartHeader(): Map<String, String>? {
        if (channel.isClosedForRead && buffer.isEmpty()) return null
        if (isFinished) return null

        val headers = mutableMapOf<String, String>()
        var foundBoundary = false

        while (true) {
            val line = readLine() ?: break

            if (!foundBoundary) {
                if (line.isBlank()) continue

                if (line.contains("--$boundary")) {
                    if (line.contains("--$boundary--")) {
                        isFinished = true
                        return null
                    }
                    foundBoundary = true
                    continue
                }
                continue
            }

            if (line.isBlank()) break

            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                headers[parts[0].trim()] = parts[1].trim()
            }
        }

        if (!foundBoundary) return null
        return headers
    }

    suspend fun readPartBodyString(): String =
        Buffer().use {
            streamPartBody(it)
            it.readUtf8()
        }

    suspend fun streamPartBody(sink: Sink) {
        val transferBuffer = ByteArray(8192)
        val delimiter = partDelimiter

        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(transferBuffer)
            if (read <= 0) break

            val chunk =
                if (buffer.isNotEmpty()) {
                    buffer + transferBuffer.copyOfRange(0, read)
                } else {
                    transferBuffer.copyOfRange(
                        0,
                        read,
                    )
                }

            val index = findBytesIndex(chunk, delimiter)

            if (index >= 0) {
                if (index > 0) {
                    sink.write(
                        Buffer().write(
                            chunk,
                            0,
                            index,
                        ),
                        index.toLong(),
                    )
                }

                buffer = chunk.copyOfRange(index, chunk.size)
                return
            } else {
                val safeLength = chunk.size - delimiter.size
                if (safeLength > 0) {
                    sink.write(
                        Buffer().write(
                            chunk,
                            0,
                            safeLength,
                        ),
                        safeLength.toLong(),
                    )
                    buffer = chunk.copyOfRange(safeLength, chunk.size)
                } else {
                    buffer = chunk
                }
            }
        }
        if (buffer.isNotEmpty()) {
            val s = buffer.decodeToString(0, 0 + buffer.size)
            if (!s.contains(boundaryBytes.decodeToString(0, 0 + boundaryBytes.size))) {
                sink.write(Buffer().write(buffer), buffer.size.toLong())
            }
            buffer = ByteArray(0)
        }
    }

    suspend fun skipPartBody() {
        streamPartBody(blackholeSink())
    }

    private suspend fun readLine(): String? {
        val sb = StringBuilder()

        var i = 0
        while (i < buffer.size) {
            if (buffer[i] == 10.toByte()) {
                val line = buffer.decodeToString(0, 0 + if (i > 0 && buffer[i - 1] == 13.toByte()) i - 1 else i)
                buffer = buffer.copyOfRange(i + 1, buffer.size)
                return line
            }
            i++
        }

        if (buffer.isNotEmpty()) {
            sb.append(buffer.decodeToString(0, 0 + buffer.size))
            buffer = ByteArray(0)
        }

        val oneByte = ByteArray(1)
        while (channel.readAvailable(oneByte) > 0) {
            val b = oneByte[0]
            if (b == 10.toByte()) {
                return sb.toString().trimEnd('\r')
            }
            sb.append(b.toInt().toChar())
        }

        if (sb.isEmpty()) return null
        return sb.toString()
    }

    private fun findBytesIndex(
        source: ByteArray,
        match: ByteArray,
    ): Int {
        if (match.isEmpty()) return -1
        for (i in 0..source.size - match.size) {
            var found = true
            for (j in match.indices) {
                if (source[i + j] != match[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}

private fun extractHeaderValue(
    headers: String,
    key: String,
): String? {
    val regex = Regex("$key=\"([^\"]*)\"")
    return regex.find(headers)?.groupValues?.get(1)
}
