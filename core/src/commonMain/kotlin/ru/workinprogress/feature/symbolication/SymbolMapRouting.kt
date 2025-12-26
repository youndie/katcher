@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.symbolication

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.String
import io.ktor.utils.io.readAvailable
import okio.Sink
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
            val boundary =
                call.request.contentType().parameter("boundary")
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing boundary")

            val parser = MultipartStreamParser(call.receiveChannel(), boundary)

            var buildUuid: String? = null
            var appKey: String? = null
            var fileProcessed = false

            while (true) {
                val headers = parser.nextPartHeader() ?: break

                val contentDisposition = headers["Content-Disposition"] ?: ""
                val name = extractHeaderValue(contentDisposition, "name")

                when (name) {
                    "appKey" -> {
                        appKey = parser.readPartBodyString().trim()
                    }

                    "buildUuid" -> {
                        buildUuid = parser.readPartBodyString().trim()
                    }

                    "mappingFile", "file" -> {
                        if (appKey == null || buildUuid == null) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                "Fields 'appKey' and 'buildUuid' must be sent before the file",
                            )
                        }

                        val app =
                            appRepository.findByApiKey(appKey)
                                ?: return@post call.respond(HttpStatusCode.Unauthorized)

                        val path = "${serverConfig.sourceMapPath}/${app.id}/$buildUuid.txt"

                        fileStorage.write(path) { fileSink ->
                            parser.streamPartBody(fileSink)
                        }

                        symbolMapRepository.save(
                            SymbolMap(
                                appId = app.id,
                                buildUuid = buildUuid,
                                type = MappingType.ANDROID_PROGUARD,
                                filePath = path,
                                createdAt = Clock.System.now().toEpochMilliseconds(),
                            ),
                        )
                        fileProcessed = true
                    }

                    else -> {
                        parser.skipPartBody()
                    }
                }
            }

            if (!fileProcessed) {
                return@post call.respond(HttpStatusCode.BadRequest, "No file uploaded")
            }

            call.respond(HttpStatusCode.Created)
        } catch (e: Exception) {
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

    suspend fun readPartBodyString(): String {
        val memBuffer = okio.Buffer()
        streamPartBody(memBuffer)
        return memBuffer.readUtf8()
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
                    sink.write(okio.Buffer().write(chunk, 0, index), index.toLong())
                }

                buffer = chunk.copyOfRange(index, chunk.size)
                return
            } else {
                val safeLength = chunk.size - delimiter.size
                if (safeLength > 0) {
                    sink.write(okio.Buffer().write(chunk, 0, safeLength), safeLength.toLong())
                    buffer = chunk.copyOfRange(safeLength, chunk.size)
                } else {
                    buffer = chunk
                }
            }
        }
        if (buffer.isNotEmpty()) {
            val s = String(buffer)
            if (!s.contains(String(boundaryBytes))) {
                sink.write(okio.Buffer().write(buffer), buffer.size.toLong())
            }
            buffer = ByteArray(0)
        }
    }

    suspend fun skipPartBody() {
        streamPartBody(okio.blackholeSink())
    }

    private suspend fun readLine(): String? {
        val sb = StringBuilder()

        var i = 0
        while (i < buffer.size) {
            if (buffer[i] == 10.toByte()) { // \n
                val line = String(buffer, 0, if (i > 0 && buffer[i - 1] == 13.toByte()) i - 1 else i)
                buffer = buffer.copyOfRange(i + 1, buffer.size)
                return line
            }
            i++
        }
        if (buffer.isNotEmpty()) {
            sb.append(String(buffer))
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
