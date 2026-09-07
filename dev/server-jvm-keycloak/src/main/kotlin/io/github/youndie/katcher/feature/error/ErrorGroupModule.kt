package io.github.youndie.katcher.feature.error

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import io.github.youndie.katcher.feature.error.data.ErrorGroupRepositoryImpl
import io.github.youndie.katcher.feature.error.data.ErrorGroupViewedRepositoryImpl

val errorGroupModule =
    module {
        singleOf(::ReportsQueueService)
        singleOf(::ProcessReportUseCase)
        singleOf(::ErrorGroupRepositoryImpl).bind<ErrorGroupRepository>()
        singleOf(::ErrorGroupViewedRepositoryImpl).bind<ErrorGroupViewedRepository>()
    }
