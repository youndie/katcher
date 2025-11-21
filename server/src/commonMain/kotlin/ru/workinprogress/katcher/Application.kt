package ru.workinprogress.katcher

import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.smyrgeorge.sqlx4k.sqlite.sqlite
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.auth.headerUserIdAuth
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ErrorGroupViewedRepository
import ru.workinprogress.feature.error.ProcessReportUseCase
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.error.data.ErrorGroupViewedRepositoryImpl
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.report.data.ReportRepositoryImpl
import ru.workinprogress.feature.user.UserRepository
import ru.workinprogress.feature.user.data.UserRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.ErrorGroupCrudRepositoryImpl
import ru.workinprogress.katcher.db.UsersCrudRepositoryImpl
import ru.workinprogress.katcher.db.commands

fun Application.module() {
    val config = getServerConfig()
    val db = initDb(config)
    common()
    initDi(db)
    initAuth()
    configureRouting()
}

fun initDb(config: ServerConfig): ISQLite {
    val options =
        ConnectionPool.Options
            .builder()
            .maxConnections(10)
            .build()

    val db =
        sqlite(
            url = "sqlite://" + config.sqlitePath,
            options = options,
        )

    runBlocking {
        db.transaction {
            commands.forEach { command ->
                db.execute(command)
            }
        }
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

fun Application.initDi(db: ISQLite) {
    dependencies {
        provide<AppRepository> {
            AppRepositoryImpl(db, AppsCrudRepositoryImpl)
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
            ProcessReportUseCase(resolve(), resolve())
        }
        provide<UserRepository> {
            UserRepositoryImpl(db, UsersCrudRepositoryImpl)
        }
    }
}
