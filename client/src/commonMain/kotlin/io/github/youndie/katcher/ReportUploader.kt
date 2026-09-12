package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.CreateReportParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Очередь отчётов, лежащих на диске, и единственный, кто их отправляет.
 *
 * Отправку просят сигналом ([requestUpload]) — её делает [work] в фоне. Отдельно от него очередь
 * умеет отдать себя целиком и сказать, получилось ли: это [flush], которым хост, у которого не
 * будет следующего запуска, забирает отчёт перед выходом из процесса.
 *
 * `runBlocking` в [flushBlocking] — то, ради чего это вообще написано: обработчик необработанного
 * исключения не suspend и последним, что успевает сделать процесс. Общий код зовёт его потому, что
 * среди объявленных таргетов клиента нет ни JS, ни Wasm — а у `:shared` wasmJs есть, так что с этой
 * стороны такой таргет однажды приедет. Приедет — [flushBlocking] переедет в expect/actual.
 */
internal class ReportUploader(
    private val fileSystem: KatcherFileSystem,
    private val log: (String) -> Unit,
    private val send: suspend (CreateReportParams) -> Boolean,
) {
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val drainLock = Mutex()

    fun requestUpload() {
        signal.trySend(Unit)
    }

    suspend fun work() {
        for (ignored in signal) {
            log("Worker woke up. Checking disk...")
            drain()
        }
    }

    /**
     * Отдаёт очередь и отвечает, пуста ли она теперь. `false` — либо сеть отказала, либо
     * кончился [grace]: отчёт остался на диске и уедет при следующей попытке, если она будет.
     * Нулевой [grace] не делает ничего и отвечает `false`.
     */
    suspend fun flush(grace: Duration): Boolean = withTimeoutOrNull(grace) { drain() } == true

    fun flushBlocking(grace: Duration): Boolean = runBlocking(Dispatchers.IO) { flush(grace) }

    /**
     * Первый отказ останавливает проход: очередь упорядочена по времени, и отправлять следующий
     * отчёт в сеть, которая только что отказала, — значит выбросить бюджет ожидания на неё же.
     */
    private suspend fun drain(): Boolean =
        drainLock.withLock {
            for (report in fileSystem.getReports()) {
                if (!send(report.params)) {
                    log("Network error. Retrying later.")
                    return@withLock false
                }
                fileSystem.deleteReport(report.fileName)
            }
            true
        }
}
