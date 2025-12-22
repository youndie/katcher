package ru.workinprogress.katcher.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
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
            uploadMultipart(targetUrl, file, uuid, key)
            logger.lifecycle("✅ Mapping uploaded successfully!")
        } catch (e: Exception) {
            logger.error("❌ Failed to upload mapping: ${e.message}")
            throw e
        }
    }

    private fun uploadMultipart(
        targetUrl: String,
        file: File,
        uuid: String,
        key: String,
    ) {
        val boundary = "---KatcherBoundary${System.currentTimeMillis()}"
        val lineFeed = "\r\n"

        val connection = URL(targetUrl).openConnection() as HttpURLConnection
        connection.useCaches = false
        connection.doOutput = true
        connection.doInput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("User-Agent", "Katcher-Gradle-Plugin")

        val outputStream = connection.outputStream
        val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

        addFormField(writer, "appKey", key, boundary, lineFeed)
        addFormField(writer, "buildUuid", uuid, boundary, lineFeed)
        addFormField(writer, "type", "ANDROID_PROGUARD", boundary, lineFeed)

        writer.append("--$boundary").append(lineFeed)
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"").append(lineFeed)
        writer.append("Content-Type: text/plain").append(lineFeed)
        writer.append(lineFeed)
        writer.flush()

        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        }

        writer.append(lineFeed)
        writer.flush()

        writer.append("--$boundary--").append(lineFeed)
        writer.close()

        val status = connection.responseCode
        if (status !in 200..299) {
            throw RuntimeException("Server returned HTTP $status: ${connection.responseMessage}")
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
        writer.append("Content-Type: text/plain; charset=UTF-8").append(lineFeed)
        writer.append(lineFeed)
        writer.append(value).append(lineFeed)
        writer.flush()
    }
}
