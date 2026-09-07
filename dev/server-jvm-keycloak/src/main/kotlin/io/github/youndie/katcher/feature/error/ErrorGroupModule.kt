package io.github.youndie.katcher.feature.error

import io.github.youndie.katcher.feature.error.data.ErrorGroupRepositoryImpl
import io.github.youndie.katcher.feature.error.data.ErrorGroupViewedRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val errorGroupModule =
    module {
        singleOf(::ReportsQueueService)
        singleOf(::ProcessReportUseCase)
        singleOf(::ErrorGroupRepositoryImpl).bind<ErrorGroupRepository>()
        singleOf(::ErrorGroupViewedRepositoryImpl).bind<ErrorGroupViewedRepository>()
    }
