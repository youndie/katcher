package io.github.youndie.katcher

import io.github.youndie.katcher.feature.app.appModule
import io.github.youndie.katcher.feature.error.errorGroupModule
import io.github.youndie.katcher.feature.report.reportModule
import io.github.youndie.katcher.feature.symbolication.symbolicationModule
import io.github.youndie.katcher.feature.user.userModule
import org.koin.core.module.Module

fun appModules(): List<Module> = listOf(userModule, reportModule, appModule, errorGroupModule, symbolicationModule)
