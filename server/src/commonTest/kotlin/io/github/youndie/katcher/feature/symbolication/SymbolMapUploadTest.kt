package io.github.youndie.katcher.feature.symbolication

import io.github.youndie.katcher.ServerConfig
import io.github.youndie.katcher.feature.app.AppKey
import io.github.youndie.katcher.feature.app.AppKeyRepository
import io.github.youndie.katcher.retrace.MappingFileStorage
import io.github.youndie.katcher.retrace.MappingStore
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.resources.Resources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import okio.Buffer
import okio.BufferedSource
import okio.Sink
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The upload route through the engine that ships — CIO, bound to a real port — rather than a test
 * engine. The route used to carry its own multipart parser because CIO on Kotlin/Native could not
 * receive multipart at all (KTOR-7361); Ktor 3.6.0 moved the parsing into common code (KTOR-9784),
 * and Ktor's own test for that runs on its test engine, not on CIO. `nativeTest` is the run that
 * answers for the binary.
 *
 * The request is written by hand, in the shape `dev/android-gradle-plugin`'s `UploadMappingTask`
 * sends: text fields, then the file part, chunked. A client would re-encode the body and prove
 * nothing about what that uploader puts on the wire.
 *
 * `runBlocking`, not `runTest`: a real socket under the test dispatcher's virtual clock.
 */
class SymbolMapUploadTest {
    private val boundary = "KatcherBoundary19a2b3c4d5e"

    @Test
    fun aChunkedUploadStoresTheFileByteForByte() =
        withServer { port, files, maps ->
            // Larger than any single read, and salted with what a parser trips on: CRLFs, a line
            // that starts like the delimiter, and a delimiter cut one byte short.
            val mapping =
                Random(42).nextBytes(300_000) +
                    "\r\n--${boundary.dropLast(1)}\r\n--\r\n".encodeToByteArray() +
                    Random(7).nextBytes(10_000)

            val response = post(port, uploadBody(mapping), chunked = true)

            assertEquals(201, response.status, response.body)
            assertEquals(listOf("./data/mappings/7/build-1.txt"), files.keys.toList())
            assertContentEquals(mapping, files.getValue("./data/mappings/7/build-1.txt"))
            val saved = maps.single()
            assertEquals(7, saved.appId)
            assertEquals("build-1", saved.buildUuid)
            assertEquals(MappingType.ANDROID_PROGUARD, saved.type)
        }

    @Test
    fun anUploadWithContentLengthIsAcceptedToo() =
        withServer { port, files, _ ->
            val mapping = "com.example.A -> a:\n    void run() -> b\n".encodeToByteArray()

            val response = post(port, uploadBody(mapping), chunked = false)

            assertEquals(201, response.status, response.body)
            assertContentEquals(mapping, files.values.single())
        }

    @Test
    fun anUnknownKeyIsUnauthorized() =
        withServer { port, files, _ ->
            val response = post(port, uploadBody("x".encodeToByteArray(), appKey = "revoked"))

            assertEquals(401, response.status, response.body)
            assertTrue(files.isEmpty())
        }

    @Test
    fun theFileBeforeTheFieldsIsRejected() =
        withServer { port, files, _ ->
            val body = filePart("x".encodeToByteArray()) + field("appKey", GOOD_KEY) + closing()

            val response = post(port, body)

            assertEquals(400, response.status, response.body)
            assertTrue(files.isEmpty())
        }

    @Test
    fun fieldsWithoutAFileAreRejected() =
        withServer { port, _, _ ->
            val body = field("appKey", GOOD_KEY) + field("buildUuid", "build-1") + closing()

            val response = post(port, body)

            assertEquals(400, response.status, response.body)
        }

    private fun uploadBody(
        mapping: ByteArray,
        appKey: String = GOOD_KEY,
    ): ByteArray =
        field("appKey", appKey) +
            field("buildUuid", "build-1") +
            field("type", "ANDROID_PROGUARD") +
            filePart(mapping) +
            closing()

    private fun field(
        name: String,
        value: String,
    ): ByteArray =
        (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"$name\"\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n\r\n" +
                "$value\r\n"
        ).encodeToByteArray()

    private fun filePart(content: ByteArray): ByteArray =
        (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"mappingFile\"; filename=\"mapping.txt\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
        ).encodeToByteArray() + content

    private fun closing(): ByteArray = "\r\n--$boundary--\r\n".encodeToByteArray()

    private class Response(
        val status: Int,
        val body: String,
    )

    private suspend fun post(
        port: Int,
        body: ByteArray,
        chunked: Boolean = false,
    ): Response =
        SelectorManager(Dispatchers.Default).use { selector ->
            aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port)).use { socket ->
                val output = socket.openWriteChannel(autoFlush = false)
                val head =
                    "POST /api/mappings/upload HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "Content-Type: multipart/form-data; boundary=$boundary\r\n" +
                        (if (chunked) "Transfer-Encoding: chunked\r\n" else "Content-Length: ${body.size}\r\n") +
                        "Connection: close\r\n\r\n"
                output.writeFully(head.encodeToByteArray())
                if (chunked) {
                    body.asList().chunked(8192).forEach { chunk ->
                        output.writeFully("${chunk.size.toString(16)}\r\n".encodeToByteArray())
                        output.writeFully(chunk.toByteArray())
                        output.writeFully("\r\n".encodeToByteArray())
                    }
                    output.writeFully("0\r\n\r\n".encodeToByteArray())
                } else {
                    output.writeFully(body)
                }
                output.flush()

                val raw =
                    socket
                        .openReadChannel()
                        .readBuffer()
                        .readByteArray()
                        .decodeToString()
                val status = raw.substringAfter(' ').substringBefore(' ').toInt()
                Response(status, raw.substringAfter("\r\n\r\n"))
            }
        }

    private fun withServer(
        block: suspend (
            port: Int,
            files: Map<String, ByteArray>,
            maps: List<SymbolMap>,
        ) -> Unit,
    ) = runBlocking {
        val files = mutableMapOf<String, ByteArray>()
        val maps = mutableListOf<SymbolMap>()
        val server =
            embeddedServer(CIO, port = 0, host = "127.0.0.1") {
                install(Resources)
                routing {
                    route("api") {
                        symbolMapRouting(Keys, Storage(files), Maps(maps), ServerConfig())
                    }
                }
            }
        server.startSuspend(wait = false)
        try {
            val port =
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            block(port, files, maps)
        } finally {
            server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
    }

    private object Keys : AppKeyRepository {
        override suspend fun findActiveByKey(key: String): AppKey? =
            AppKey(id = 1, appId = 7, key = key, createdAt = 0, lastUsedAt = null, revokedAt = null)
                .takeIf { key == GOOD_KEY }

        override suspend fun listByApp(appId: Int): List<AppKey> = error("unused")

        override suspend fun listAll(): Map<Int, List<AppKey>> = error("unused")

        override suspend fun issue(
            appId: Int,
            at: Long,
        ): AppKey = error("unused")

        override suspend fun revoke(
            id: Long,
            at: Long,
        ) = error("unused")

        override suspend fun markUsed(
            id: Long,
            at: Long,
        ) = error("unused")
    }

    private class Storage(
        private val files: MutableMap<String, ByteArray>,
    ) : MappingFileStorage {
        override suspend fun read(
            path: String,
            readerAction: BufferedSource.() -> MappingStore,
        ): MappingStore = error("unused")

        override suspend fun write(
            path: String,
            block: suspend (Sink) -> Unit,
        ) {
            val buffer = Buffer()
            block(buffer)
            files[path] = buffer.readByteArray()
        }
    }

    private class Maps(
        private val maps: MutableList<SymbolMap>,
    ) : SymbolMapRepository {
        override suspend fun find(
            appId: Int,
            buildUuid: String,
        ): SymbolMap? = maps.find { it.appId == appId && it.buildUuid == buildUuid }

        override suspend fun save(symbolMap: SymbolMap): Long {
            maps += symbolMap
            return maps.size.toLong()
        }
    }

    private companion object {
        const val GOOD_KEY = "good-key"
    }
}
