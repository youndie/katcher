package ru.workinprogress.katcher

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import ru.workinprogress.feature.auth.configureAuth
import ru.workinprogress.feature.auth.configureSessions
import ru.workinprogress.feature.error.launchReportQueueService
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

    configureAuth()
    configureSessions()
    configureRouting()
    launchReportQueueService(get())
}
