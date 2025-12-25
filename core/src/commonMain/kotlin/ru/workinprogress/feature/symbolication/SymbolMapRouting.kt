@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.symbolication

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
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
        val parts = call.receiveMultipartManual()

        var fileBytes: ByteArray? = null
        var buildUuid: String? = null
        var appKey: String? = null

        parts.forEach { part ->
            when (part.name) {
                "appKey" -> appKey = part.utf8Value
                "buildUuid" -> buildUuid = part.utf8Value
                "mappingFile", "file" -> fileBytes = part.bytes
            }
        }

        if (appKey == null || buildUuid == null || fileBytes == null) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }

        val app = appRepository.findByApiKey(appKey) ?: return@post call.respond(HttpStatusCode.Unauthorized)
        println("Found app: ${app.id} for appKey: $appKey")
        val path = "${serverConfig.sourceMapPath}/${app.id}/$buildUuid.txt"
        println("Writing symbol map to path: $path, buildUuid: $buildUuid")
        fileStorage.write(path, fileBytes)
        println("Symbol map written successfully")

        val id =
            symbolMapRepository.save(
                SymbolMap(
                    appId = app.id,
                    buildUuid = buildUuid,
                    type = MappingType.ANDROID_PROGUARD,
                    filePath = path,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        println("Symbol map saved to repository for buildUuid: $buildUuid, id: $id")
        call.respond(HttpStatusCode.Created)
    }
}

// Workaround https://youtrack.jetbrains.com/issue/KTOR-7361/CIO-native-receiveMultipart-throw-CannotTransformContentToTypeException
private class SimplePart(
    val name: String?,
    val filename: String?,
    val bytes: ByteArray,
) {
    val utf8Value: String get() = bytes.decodeToString()
}

private suspend fun ApplicationCall.receiveMultipartManual(): List<SimplePart> {
    val boundary =
        request.contentType().parameter("boundary")
            ?: throw IllegalArgumentException("Missing boundary")

    val channel = receiveChannel()
    val allBytes = channel.readRemaining().readByteArray()

    val parts = mutableListOf<SimplePart>()
    val boundaryBytes = "--$boundary".encodeToByteArray()
    val endBoundaryBytes = "--$boundary--".encodeToByteArray()

    val indices = findAllOccurrences(allBytes, boundaryBytes)

    for (i in 0 until indices.size - 1) {
        val start = indices[i] + boundaryBytes.size
        val end = indices[i + 1]

        var contentStart = start
        while (contentStart < end && (allBytes[contentStart] == 13.toByte() || allBytes[contentStart] == 10.toByte())) {
            contentStart++
        }

        val headerEndIndex = findHeaderEnd(allBytes, contentStart, end)

        if (headerEndIndex != -1) {
            val headerBytes = allBytes.copyOfRange(contentStart, headerEndIndex)
            val headerString = headerBytes.decodeToString()

            val name = extractHeaderValue(headerString, "name")
            val filename = extractHeaderValue(headerString, "filename")

            var bodyStart = headerEndIndex
            if (bodyStart + 4 <= end && allBytes[bodyStart] == 13.toByte() && allBytes[bodyStart + 2] == 13.toByte()) {
                bodyStart += 4
            } else if (bodyStart + 2 <= end && allBytes[bodyStart] == 10.toByte()) {
                bodyStart += 2
            }

            var bodyEnd = end
            while (bodyEnd > bodyStart && (allBytes[bodyEnd - 1] == 13.toByte() || allBytes[bodyEnd - 1] == 10.toByte())) {
                bodyEnd--
            }

            val bodyBytes = allBytes.copyOfRange(bodyStart, bodyEnd)
            parts.add(SimplePart(name, filename, bodyBytes))
        }
    }

    return parts
}

private fun findAllOccurrences(
    data: ByteArray,
    pattern: ByteArray,
): List<Int> {
    val indices = mutableListOf<Int>()
    var i = 0
    while (i <= data.size - pattern.size) {
        var match = true
        for (j in pattern.indices) {
            if (data[i + j] != pattern[j]) {
                match = false
                break
            }
        }
        if (match) {
            indices.add(i)
            i += pattern.size
        } else {
            i++
        }
    }
    return indices
}

private fun findHeaderEnd(
    data: ByteArray,
    start: Int,
    end: Int,
): Int {
    for (i in start until end - 3) {
        if (data[i] == 13.toByte() && data[i + 1] == 10.toByte() &&
            data[i + 2] == 13.toByte() && data[i + 3] == 10.toByte()
        ) {
            return i
        }
        if (data[i] == 10.toByte() && data[i + 1] == 10.toByte()) {
            return i
        }
    }
    return -1
}

private fun extractHeaderValue(
    headers: String,
    key: String,
): String? {
    val regex = Regex("$key=\"([^\"]*)\"")
    return regex.find(headers)?.groupValues?.get(1)
}
