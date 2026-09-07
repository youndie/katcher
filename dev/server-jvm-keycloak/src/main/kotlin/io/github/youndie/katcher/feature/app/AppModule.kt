package io.github.youndie.katcher.feature.app

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import io.github.youndie.katcher.feature.app.data.AppKeyRepositoryImpl
import io.github.youndie.katcher.feature.app.data.AppOverviewRepositoryImpl
import io.github.youndie.katcher.feature.app.data.AppRepositoryImpl

val appModule =
    module {
        singleOf(::AppRepositoryImpl).bind<AppRepository>()
        singleOf(::AppOverviewRepositoryImpl).bind<AppOverviewRepository>()
        singleOf(::AppKeyRepositoryImpl).bind<AppKeyRepository>()
    }
