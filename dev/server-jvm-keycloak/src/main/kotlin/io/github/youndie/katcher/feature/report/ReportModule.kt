package io.github.youndie.katcher.feature.report

import io.github.youndie.katcher.feature.report.data.ReportRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val reportModule =
    module {
        singleOf(::ReportRepositoryImpl).bind<ReportRepository>()
    }
