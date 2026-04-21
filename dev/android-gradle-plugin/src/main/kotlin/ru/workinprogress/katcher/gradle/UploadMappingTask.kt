package ru.workinprogress.katcher.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@DisableCachingByDefault(because = "Network upload tasks should not be cached remotely, but can be UP-TO-DATE locally")
abstract class UploadMappingTask : DefaultTask() {
    @get:Input
    abstract val serverUrl: Property<String>

    @get:Input
    abstract val appKey: Property<String>

    @get:Input
    abstract val buildUuid: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFile: RegularFileProperty

    @get:OutputFile
    abstract val outputMarker: RegularFileProperty

    @TaskAction
    fun upload() {
        if (!mappingFile.isPresent) {
            logger.info("Minification is disabled for this variant. No mapping file to upload.")
            return
        }

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

        try {
            uploadMultipart(URI.create(targetUrl).toURL(), file, uuid, key)
            logger.lifecycle("✅ Mapping uploaded successfully!")

            writeMarker("Uploaded mapping for UUID: $uuid\nTimestamp: ${System.currentTimeMillis()}")
        } catch (e: Exception) {
            logger.error("❌ Failed to upload mapping: ${e.message}")
            throw e
        }
    }

    private fun writeMarker(content: String) {
        val markerFile = outputMarker.get().asFile
        markerFile.parentFile.mkdirs()
        markerFile.writeText(content)
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
        connection.connectTimeout = 120_000
        connection.readTimeout = 120_000

        connection.setChunkedStreamingMode(8192)

        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("User-Agent", "Katcher-Gradle-Plugin")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Connection", "close")

        var writeException: Exception? = null

        try {
            connection.outputStream.use { outputStream ->
                val writer = outputStream.bufferedWriter(Charsets.UTF_8)

                fun addField(
                    name: String,
                    value: String,
                ) {
                    writer.write("--$boundary$lineFeed")
                    writer.write("Content-Disposition: form-data; name=\"$name\"$lineFeed")
                    writer.write("Content-Type: text/plain; charset=utf-8$lineFeed$lineFeed")
                    writer.write("$value$lineFeed")
                }

                addField("appKey", appKey)
                addField("buildUuid", buildUuid)
                addField("type", "ANDROID_PROGUARD")

                writer.write("--$boundary$lineFeed")
                writer.write("Content-Disposition: form-data; name=\"mappingFile\"; filename=\"${file.name}\"$lineFeed")
                writer.write("Content-Type: application/octet-stream$lineFeed$lineFeed")

                writer.flush()

                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream, 8192)
                }

                outputStream.flush()

                writer.write("$lineFeed--$boundary--$lineFeed")
                writer.flush()
            }
        } catch (e: Exception) {
            writeException = e
        }

        val responseCode =
            try {
                connection.responseCode
            } catch (e: Exception) {
                throw RuntimeException(
                    "Connection closed completely: ${writeException?.message ?: e.message}",
                    writeException ?: e,
                )
            }

        if (responseCode in 200..299) {
            return
        } else {
            val errorBody =
                try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: connection.inputStream?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
            logger.warn("Server returned HTTP $responseCode: ${connection.responseMessage}. Details: $errorBody")
        }
    }
}
