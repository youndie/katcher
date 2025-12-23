package ru.workinprogress.katcher.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

abstract class UploadMappingTask : DefaultTask() {
    @get:Input
    abstract val serverUrl: Property<String>

    @get:Input
    abstract val appKey: Property<String>

    @get:Input
    abstract val buildUuid: Property<String>

    @get:InputFile
    abstract val mappingFile: RegularFileProperty

    @TaskAction
    fun upload() {
        val file = mappingFile.get().asFile
        if (!file.exists()) {
            logger.warn("Mapping file not found at ${file.absolutePath}. Skipping upload.")
            return
        }

        val targetUrl = "${serverUrl.get()}/api/mappings/upload"
        val uuid = buildUuid.get()
        val key = appKey.get()

        logger.lifecycle("Uploading mapping file to Katcher...")
        logger.lifecycle("  File: ${file.name} (${file.length() / 1024} KB)")
        logger.lifecycle("  Build UUID: $uuid")
        logger.lifecycle("  Target: $targetUrl")

        try {
            uploadMultipart(URI.create(targetUrl).toURL(), file, uuid, key)
            logger.lifecycle("✅ Mapping uploaded successfully!")
        } catch (e: Exception) {
            logger.error("❌ Failed to upload mapping: ${e.message}")
            throw e
        }
    }

    private fun uploadMultipart(
        url: URL,
        file: File,
        buildUuid: String,
        appKey: String,
    ) {
        val boundary = "KatcherBoundary${System.currentTimeMillis().toString(16)}"
        val lineFeed = "\r\n"

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.useCaches = false
        connection.instanceFollowRedirects = false

        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("User-Agent", "Katcher-Gradle-Plugin")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Connection", "close") // Избегаем проблем с keep-alive на CIO

        try {
            val outputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

            addFormField(writer, "appKey", appKey, boundary, lineFeed)
            addFormField(writer, "buildUuid", buildUuid, boundary, lineFeed)
            addFormField(writer, "type", "ANDROID_PROGUARD", boundary, lineFeed)

            val mimeType =
                when (file.extension.lowercase()) {
                    "txt" -> "text/plain"
                    else -> "application/octet-stream"
                }

            writer.append("--$boundary").append(lineFeed)
            writer.append("Content-Disposition: form-data; name=\"mappingFile\"; filename=\"${file.name}\"").append(lineFeed)
            writer.append("Content-Type: $mimeType").append(lineFeed)
            writer.append(lineFeed)
            writer.flush()

            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.flush()

            writer.append(lineFeed)
            writer.flush()

            writer.append("--$boundary--").append(lineFeed)
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody =
                    try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (e: Exception) {
                        try {
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } catch (e2: Exception) {
                            null
                        }
                    }
                throw RuntimeException("Server returned HTTP $responseCode: ${connection.responseMessage}. Details: $errorBody")
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to upload mapping file: ${e.message}", e)
        }
    }

    private fun addFormField(
        writer: PrintWriter,
        name: String,
        value: String,
        boundary: String,
        lineFeed: String,
    ) {
        writer.append("--$boundary").append(lineFeed)
        writer.append("Content-Disposition: form-data; name=\"$name\"").append(lineFeed)
        writer.append("Content-Type: text/plain").append(lineFeed)
        writer.append(lineFeed)
        writer.append(value).append(lineFeed)
        writer.flush()
    }
}
