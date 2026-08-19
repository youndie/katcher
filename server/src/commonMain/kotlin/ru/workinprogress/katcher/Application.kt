package ru.workinprogress.katcher

import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.smyrgeorge.sqlx4k.sqlite.sqlite
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import ru.workinprogress.feature.app.AppOverviewRepository
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.data.AppOverviewRepositoryImpl
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.auth.headerUserIdAuth
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ErrorGroupViewedRepository
import ru.workinprogress.feature.error.ProcessReportUseCase
import ru.workinprogress.feature.error.ReportsQueueService
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.error.data.ErrorGroupViewedRepositoryImpl
import ru.workinprogress.feature.error.launchReportQueueService
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.data.ReportRepositoryImpl
import ru.workinprogress.feature.symbolication.AndroidR8Symbolicator
import ru.workinprogress.feature.symbolication.MappingType
import ru.workinprogress.feature.symbolication.SymbolMapRepository
import ru.workinprogress.feature.symbolication.SymbolicationService
import ru.workinprogress.feature.symbolication.data.SymbolMapRepositoryImpl
import ru.workinprogress.feature.user.UserRepository
import ru.workinprogress.feature.user.data.UserRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.ErrorGroupCrudRepositoryImpl
import ru.workinprogress.katcher.db.SymbolMapCrudRepositoryImpl
import ru.workinprogress.katcher.db.UsersCrudRepositoryImpl
import ru.workinprogress.katcher.db.migrateDb
import ru.workinprogress.katcher.mcp.KatcherMcpServer
import ru.workinprogress.katcher.mcp.installMcp
import ru.workinprogress.metrik.agent.Metrik
import ru.workinprogress.retrace.MappingFileStorage
import ru.workinprogress.retrace.MappingFileStorageOkio

suspend fun Application.module() {
    val config = getServerConfig()
    val db = initDb(config)
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
