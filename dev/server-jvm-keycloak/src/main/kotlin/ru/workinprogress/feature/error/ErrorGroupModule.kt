package ru.workinprogress.feature.error

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.error.data.ErrorGroupRepositoryImpl
import ru.workinprogress.feature.error.data.ErrorGroupViewedRepositoryImpl

val errorGroupModule =
    module {
        singleOf(::ReportsQueueService)
        singleOf(::ProcessReportUseCase)
        singleOf(::ErrorGroupRepositoryImpl).bind<ErrorGroupRepository>()
        singleOf(::ErrorGroupViewedRepositoryImpl).bind<ErrorGroupViewedRepository>()
    }
