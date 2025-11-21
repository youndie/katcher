package ru.workinprogress.feature.app

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.app.data.AppRepositoryImpl

val appModule =
    module {
        singleOf(::AppRepositoryImpl).bind<AppRepository>()
    }
