package io.github.youndie.katcher

import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.smyrgeorge.sqlx4k.sqlite.sqlite
import io.github.youndie.katcher.db.AppsCrudRepositoryImpl
import io.github.youndie.katcher.db.ErrorGroupCrudRepositoryImpl
import io.github.youndie.katcher.db.SymbolMapCrudRepositoryImpl
import io.github.youndie.katcher.db.UsersCrudRepositoryImpl
import io.github.youndie.katcher.db.migrateDb
import io.github.youndie.katcher.feature.app.AppKeyRepository
import io.github.youndie.katcher.feature.app.AppOverviewRepository
import io.github.youndie.katcher.feature.app.AppRepository
import io.github.youndie.katcher.feature.app.data.AppKeyRepositoryImpl
import io.github.youndie.katcher.feature.app.data.AppOverviewRepositoryImpl
import io.github.youndie.katcher.feature.app.data.AppRepositoryImpl
import io.github.youndie.katcher.feature.auth.headerUserIdAuth
import io.github.youndie.katcher.feature.error.ErrorGroupRepository
import io.github.youndie.katcher.feature.error.ErrorGroupViewedRepository
import io.github.youndie.katcher.feature.error.ProcessReportUseCase
import io.github.youndie.katcher.feature.error.ReportsQueueService
import io.github.youndie.katcher.feature.error.data.ErrorGroupRepositoryImpl
import io.github.youndie.katcher.feature.error.data.ErrorGroupViewedRepositoryImpl
import io.github.youndie.katcher.feature.error.launchReportQueueService
import io.github.youndie.katcher.feature.report.ReportRepository
import io.github.youndie.katcher.feature.report.data.ReportRepositoryImpl
import io.github.youndie.katcher.feature.symbolication.AndroidR8Symbolicator
import io.github.youndie.katcher.feature.symbolication.MappingType
import io.github.youndie.katcher.feature.symbolication.SymbolMapRepository
import io.github.youndie.katcher.feature.symbolication.SymbolicationService
import io.github.youndie.katcher.feature.symbolication.data.SymbolMapRepositoryImpl
import io.github.youndie.katcher.feature.user.UserRepository
import io.github.youndie.katcher.feature.user.data.UserRepositoryImpl
import io.github.youndie.katcher.mcp.KatcherMcpServer
import io.github.youndie.katcher.mcp.installMcp
import io.github.youndie.katcher.retrace.MappingFileStorage
import io.github.youndie.katcher.retrace.MappingFileStorageOkio
import io.github.youndie.kore.generated.KoreBuildIdentity
import io.github.youndie.kore.ktor.installKoreProbes
import io.github.youndie.kore.ktor.installKoreVersion
import io.github.youndie.kore.ktor.installShutdownRefusal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import ru.workinprogress.metrik.agent.Metrik

/**
 * Everything this server is, given a database that is already open and the gates that answer for it.
 *
 * **The database is a parameter now, and that is the shutdown talking.** It used to be opened here,
 * which left `main` with no handle on the pool and therefore no way to close it *after* the engine
 * had drained — the ordering that `ApplicationStopping` gets backwards on Kotlin/Native (kore's
 * research §1.1, and the reason katcher takes kore at all).
 */
suspend fun Application.module(
    db: ISQLite,
    config: ServerConfig,
    probes: KatcherProbes,
) {
    // BEFORE the probes and before the routes. An interceptor installed later would let through
    // every call that arrived first, and the one request this must not miss is the first one after
    // readiness has gone false. It leaves kore's own routes alone — a 503 from `/health/live` is a
    // failed liveness probe, which restarts the pod in the middle of the shutdown it is reporting.
    installShutdownRefusal(isShuttingDown = { probes.readiness.isShuttingDown })
    installKoreProbes(probes.startup, probes.readiness, probes.liveness)

    // The version and the commit, compiled in by the Gradle plugin because Kotlin/Native has neither
    // resources nor a manifest to read them from.
    installKoreVersion(KoreBuildIdentity)

    common()
    initDi(db, config)
    initAuth()
    configureRouting()
    installMcp(config, KatcherMcpServer(dependencies.resolve(), dependencies.resolve(), dependencies.resolve()))
    installMetrik(config)
    launchReportQueueService(dependencies.resolve())
}

/**
 * Мониторинг — только если задан endpoint.
 *
 * Без него плагин не ставится вовсе: ничего не меряется и никуда не отправляется. katcher обязан
 * подниматься без metrik, иначе получается зависимость сервиса от наблюдателя.
 *
 * Агент не блокирует запрос и не бросает исключений в чужой пайплайн: если сервер недоступен или
 * очередь переполнена, он считает потерю и продолжает отдавать трафик.
 */
private fun Application.installMetrik(config: ServerConfig) {
    val endpoint = config.metrikEndpoint ?: return
    val key = config.metrikKey ?: return

    install(Metrik) {
        service = config.metrikService
        apiKey = key
        this.endpoint = endpoint
        config.metrikRelease?.let { release = it }
    }
}

fun initDb(config: ServerConfig): ISQLite {
    val options =
        ConnectionPool.Options
            .builder()
            // Двойка, а не десятка: sqlx4k заводит отдельный OS-поток на соединение, плюс на каждое
            // же кэш страниц SQLite и кэш подготовленных выражений. Замер на metrik (пять парных
            // повторов, одинаковая нагрузка) дал при пуле 2 против 10 минус 59 МиБ полки и при этом
            // плюс 10% запросов в секунду: писатель в SQLite всё равно один, и лишние соединения
            // делят тот же лок. Десятка была дефолтом sqlx, а не выбором.
            .maxConnections(2)
            .build()

    val dbPath = config.sqlitePath.toPath()
    val fileSystem = FileSystem.SYSTEM
    if (!fileSystem.exists(dbPath)) {
        val parent = dbPath.parent
        if (parent != null && !fileSystem.exists(parent)) {
            fileSystem.createDirectories(parent)
        }
        fileSystem.write(dbPath) {
            // Create empty file
        }
    }

    val db =
        sqlite(
            url = "sqlite://" + config.sqlitePath,
            options = options,
        )

    runBlocking {
        db.migrateDb()
    }

    return db
}

fun Application.initAuth() {
    runBlocking {
        val repo: UserRepository = dependencies.resolve()
        install(Authentication) {
            headerUserIdAuth(repo)
        }
    }
}

fun Application.initDi(
    db: ISQLite,
    serverConfig: ServerConfig,
) {
    dependencies {
        provide { serverConfig }
        provide<AppRepository> {
            AppRepositoryImpl(db, AppsCrudRepositoryImpl)
        }
        provide<AppOverviewRepository> {
            AppOverviewRepositoryImpl(db)
        }
        provide<AppKeyRepository> {
            AppKeyRepositoryImpl(db)
        }
        provide<ErrorGroupRepository> {
            ErrorGroupRepositoryImpl(db, ErrorGroupCrudRepositoryImpl)
        }
        provide<ErrorGroupViewedRepository> {
            ErrorGroupViewedRepositoryImpl(db)
        }
        provide<ReportRepository> {
            ReportRepositoryImpl(db)
        }
        provide<ProcessReportUseCase> {
            ProcessReportUseCase(
                resolve(),
                resolve(),
                resolve(),
                resolve(),
            )
        }
        provide<UserRepository> {
            UserRepositoryImpl(db, UsersCrudRepositoryImpl)
        }
        provide<ReportsQueueService> {
            ReportsQueueService(resolve())
        }
        provide<SymbolMapRepository> {
            SymbolMapRepositoryImpl(db, SymbolMapCrudRepositoryImpl)
        }
        provide<MappingFileStorage> {
            MappingFileStorageOkio
        }
        provide<SymbolicationService> {
            SymbolicationService(
                symbolMapRepository = resolve(),
                fileStorage = resolve(),
                strategies =
                    mapOf(
                        MappingType.ANDROID_PROGUARD to AndroidR8Symbolicator(),
                    ),
            )
        }
    }
}
