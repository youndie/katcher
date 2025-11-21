package ru.workinprogress.katcher

import org.koin.core.module.Module
import ru.workinprogress.feature.app.appModule
import ru.workinprogress.feature.error.errorGroupModule
import ru.workinprogress.feature.report.reportModule
import ru.workinprogress.feature.user.userModule

fun appModules(): List<Module> = listOf(userModule, reportModule, appModule, errorGroupModule)
