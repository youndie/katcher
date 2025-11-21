package ru.workinprogress.feature.report

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.report.data.ReportRepositoryImpl

val reportModule =
    module {
        singleOf(::ReportRepositoryImpl).bind<ReportRepository>()
    }
