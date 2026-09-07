package io.github.youndie.katcher.feature.report

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import io.github.youndie.katcher.feature.report.data.ReportRepositoryImpl

val reportModule =
    module {
        singleOf(::ReportRepositoryImpl).bind<ReportRepository>()
    }
