package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.CreateReportParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReportUploaderTest {
    private val sent = mutableListOf<String>()

    @Test
    fun `test flush delivers the queue and answers that it is empty`() {
        val fileSystem = InMemoryKatcherFileSystem(listOf("first", "second"))
        val uploader = uploader(fileSystem) { true }

        val delivered = runBlocking { uploader.flush(GRACE) }

        assertTrue(delivered)
        assertEquals(listOf("first", "second"), sent)
        assertEquals(emptyList(), fileSystem.fileNames)
    }

    // Отказ сети — не «доставлено». Хост, который выключается, обязан услышать разницу: отчёт
    // остался на диске, и если следующего запуска не будет, он не уедет никогда.
    @Test
    fun `test flush answers false and keeps the report when the network refuses`() {
        val fileSystem = InMemoryKatcherFileSystem(listOf("boom"))
        val uploader = uploader(fileSystem) { false }

        val delivered = runBlocking { uploader.flush(GRACE) }

        assertFalse(delivered)
        assertEquals(listOf("crash_000.json"), fileSystem.fileNames)
    }

    // Первый отказ останавливает проход: второй отчёт уходить в ту же сеть не пробует.
    @Test
    fun `test flush stops at the first refusal`() {
        val fileSystem = InMemoryKatcherFileSystem(listOf("first", "second"))
        val uploader = uploader(fileSystem) { it.message == "first" }

        assertFalse(runBlocking { uploader.flush(GRACE) })

        assertEquals(listOf("first"), sent)
        assertEquals(listOf("crash_001.json"), fileSystem.fileNames)
    }

    @Test
    fun `test flush gives up when the grace runs out`() {
        val fileSystem = InMemoryKatcherFileSystem(listOf("boom"))
        val uploader =
            uploader(fileSystem) {
                delay(10.seconds)
                true
            }

        val delivered = runBlocking { uploader.flush(100.milliseconds) }

        assertFalse(delivered)
        assertEquals(listOf("crash_000.json"), fileSystem.fileNames)
    }

    // Ноль — это «не ждать»: ровно то, что стоит в конфигурации по умолчанию, и мобильный клиент
    // не должен из-за неё задержать крашащийся поток даже на попытку.
    @Test
    fun `test zero grace sends nothing`() {
        val fileSystem = InMemoryKatcherFileSystem(listOf("boom"))
        val uploader = uploader(fileSystem) { true }

        val delivered = runBlocking { uploader.flush(Duration.ZERO) }

        assertFalse(delivered)
        assertEquals(emptyList(), sent)
        assertEquals(listOf("crash_000.json"), fileSystem.fileNames)
    }

    @Test
    fun `test flush of an empty queue answers true`() {
        assertTrue(runBlocking { uploader(InMemoryKatcherFileSystem()) { true }.flush(GRACE) })
    }

    // Блокирующий вариант — то, что зовёт фатальный путь: он не suspend и должен вернуть ответ
    // тому же потоку, который сейчас умрёт.
    @Test
    fun `test flushBlocking delivers from a thread that cannot suspend`() {
        val fileSystem = InMemoryKatcherFileSystem(listOf("boom"))

        val delivered = uploader(fileSystem) { true }.flushBlocking(GRACE)

        assertTrue(delivered)
        assertEquals(listOf("boom"), sent)
        assertEquals(emptyList(), fileSystem.fileNames)
    }

    @Test
    fun `test flushBlocking gives up when the grace runs out`() {
        val fileSystem = InMemoryKatcherFileSystem(listOf("boom"))
        val uploader =
            uploader(fileSystem) {
                delay(10.seconds)
                true
            }

        assertFalse(uploader.flushBlocking(100.milliseconds))
        assertEquals(listOf("crash_000.json"), fileSystem.fileNames)
    }

    private fun uploader(
        fileSystem: KatcherFileSystem,
        send: suspend (CreateReportParams) -> Boolean,
    ) = ReportUploader(fileSystem, { }, { params ->
        val accepted = send(params)
        if (accepted) sent += params.message
        accepted
    })

    private companion object {
        // Заведомо больше, чем нужно этим тестам: они меряют ответ очереди, а не скорость машины.
        private val GRACE = 30.seconds
    }
}
