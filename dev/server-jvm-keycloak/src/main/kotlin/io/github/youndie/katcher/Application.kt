package io.github.youndie.katcher

import io.github.youndie.katcher.data.initDatabase
import io.github.youndie.katcher.feature.auth.configureAuth
import io.github.youndie.katcher.feature.auth.configureSessions
import io.github.youndie.katcher.feature.error.launchReportQueueService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
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
    install(CallLogging)

    configureAuth()
    configureSessions()
    configureRouting()
    launchReportQueueService(get())
}
