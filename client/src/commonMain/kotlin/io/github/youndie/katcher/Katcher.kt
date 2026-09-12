package io.github.youndie.katcher

import io.github.youndie.katcher.feature.report.Breadcrumb
import io.github.youndie.katcher.feature.report.CreateReportParams
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration

internal expect fun setupPlatformHandler()

public object Katcher {
    private val isCrashing = atomic(false)
    private var config: KatcherConfig = KatcherConfig()
    private const val LOGO = """📡"""
    private const val MAX_BREADCRUMBS = 50
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _breadcrumbs = atomic(emptyList<Breadcrumb>())

    // Хранилище и очередь появляются в start(): каталог известен только из конфигурации, а до неё
    // писать всё равно некуда — catch() без start() и раньше ничего не делал.
    private val fileSystem = atomic<KatcherFileSystem?>(null)
    private val uploader = atomic<ReportUploader?>(null)

    public val breadcrumbs: List<Breadcrumb>
        get() = _breadcrumbs.value

    internal val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 7_000
                connectTimeoutMillis = 3_000
                socketTimeoutMillis = 5_000
            }
            install(HttpRequestRetry) {
                maxRetries = 2
                retryIf { _, response -> !response.status.isSuccess() }
                retryOnExceptionIf { _, _ -> true }
                exponentialDelay(maxDelayMs = 3_000)
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                url { takeFrom(config.remoteHost) }
            }
        }
    }

    public fun start(configure: KatcherConfig.() -> Unit) {
        val newConfig = KatcherConfig().apply(configure)

        if (newConfig.appKey.isEmpty() || newConfig.remoteHost.isEmpty()) {
            println("$LOGO Configuration error: appKey and remoteHost are required.")
            return
        }
        // Каталог отчётов проверяется здесь, а не при первом краше. Проверка при краше попадает
        // в catch внутри `catch()`, печатает строчку и не сигналит выгрузку: снаружи это выглядит
        // как настроенный репортер, которому нечего отправлять.
        val storage = createFileSystem(newConfig.cacheDir)
        val prepared = runCatching { storage.prepare() }
        prepared.exceptionOrNull()?.let { failure ->
            println("$LOGO Storage error: ${failure.message}. Reports cannot be stored, Katcher is not started.")
            return
        }

        config = newConfig
        fileSystem.value = storage
        val queue = ReportUploader(storage, ::debugLog, ::sendReport)
        uploader.value = queue

        setupPlatformHandler()
        clearBreadcrumbs()

        // Повторный start() оставил бы прежнего работника на прежнем канале: он больше никогда не
        // получит сигнала, а значит просто занял бы поток до конца процесса.
        scope.coroutineContext.cancelChildren()
        scope.launch {
            queue.work()
        }

        queue.requestUpload()

        if (config.isDebug) println("$LOGO Katcher initialized. Storage ready.")
    }

    public fun catch(
        throwable: Throwable,
        context: Map<String, String> = emptyMap(),
    ) {
        record(throwable, context)
    }

    /**
     * Отдаёт всё, что лежит на диске, и отвечает, пуста ли очередь. Для хоста, который выключается
     * и знает, что следующего запуска на этой файловой системе может не быть: зовётся последним
     * в группе телеметрии, ограничен сверху [grace].
     *
     * `false` — либо сеть отказала, либо кончился [grace]; отчёт остался на диске. `true` до
     * [start] означает ровно «отдавать нечего»: каталог ещё не выбран.
     */
    public suspend fun flush(grace: Duration): Boolean = uploader.value?.flush(grace) ?: true

    @Suppress(
        "ktlint:kapkan:wall-clock",
        "часы устройства — единственные, что есть у SDK; время прихода сервер ставит сам",
    )
    public fun addBreadcrumb(
        message: String,
        type: String = "info",
        data: Map<String, String>? = null,
    ) {
        val newBreadcrumb =
            Breadcrumb(
                timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                type = type,
                message = message,
                data = data,
            )

        _breadcrumbs.update { current ->
            val next = current + newBreadcrumb
            if (next.size > MAX_BREADCRUMBS) next.drop(1) else next
        }
    }

    public fun clearBreadcrumbs() {
        _breadcrumbs.value = emptyList()
    }

    /**
     * Путь падения, которое убивает процесс: обработчики платформы зовут его вместо [catch].
     * Отличие одно — здесь отправку ждут, потому что ждать её больше негде: воркер живёт на
     * [Dispatchers.IO] и до следующего своего шага не доживёт.
     */
    internal fun catchFatal(throwable: Throwable) {
        record(throwable, emptyMap())

        val grace = config.crashUploadGrace
        if (grace <= Duration.ZERO) return

        val delivered = uploader.value?.flushBlocking(grace) ?: return
        if (!delivered && config.isDebug) {
            println("$LOGO Crash report did not leave the process within $grace. It stays on disk.")
        }
    }

    private fun record(
        throwable: Throwable,
        context: Map<String, String>,
    ) {
        if (config.appKey.isEmpty()) return
        val storage = fileSystem.value ?: return

        val first = isCrashing.compareAndSet(expect = false, update = true)
        if (!first) return

        try {
            val params =
                CreateReportParams(
                    appKey = config.appKey,
                    message = throwable.message.toString(),
                    stacktrace = throwable.stackTraceToString(),
                    release = config.release,
                    environment = config.environment,
                    context = context,
                    breadcrumbs = breadcrumbs,
                )

            storage.saveReport(params)

            if (config.isDebug) println("$LOGO Report saved to disk. Signal sent.")

            uploader.value?.requestUpload()
        } catch (e: Exception) {
            println("$LOGO Failed to save crash report: ${e.message}")
        } finally {
            isCrashing.value = false
        }
    }

    private fun debugLog(message: String) {
        if (config.isDebug) println("$LOGO $message")
    }

    private suspend fun sendReport(params: CreateReportParams): Boolean =
        try {
            val response: HttpResponse =
                httpClient.post("api/reports") {
                    setBody(params)
                }
            if (response.status.isSuccess()) {
                if (config.isDebug) println("$LOGO Sent: ${params.message.take(20)}...")
                true
            } else {
                if (config.isDebug) println("$LOGO Server rejected: ${response.status}")
                false
            }
        } catch (e: CancellationException) {
            // A cancelled send is not a report the server refused. Answering `false` here tells the
            // caller the transmission failed, and the crash it was carrying is dropped on that word.
            throw e
        } catch (e: Exception) {
            if (config.isDebug) println("$LOGO Transmission failed: ${e.message}")
            false
        }
}
