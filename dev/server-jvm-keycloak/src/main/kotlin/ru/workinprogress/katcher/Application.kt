package ru.workinprogress.katcher

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import ru.workinprogress.feature.auth.configureAuth
import ru.workinprogress.feature.auth.configureSessions
import ru.workinprogress.feature.error.ReportsQueueService
import ru.workinprogress.katcher.data.initDatabase
import kotlin.io.encoding.ExperimentalEncodingApi

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@OptIn(ExperimentalEncodingApi::class)
fun Application.module() {
    initDatabase()
    common()
    install(Koin) {
        slf4jLogger()
        modules(appModules())
    }

    val reportsQueueService = get<ReportsQueueService>()
    launch(Dispatchers.Default) { reportsQueueService.work() }

    configureAuth()
    configureSessions()
    configureRouting()
}
