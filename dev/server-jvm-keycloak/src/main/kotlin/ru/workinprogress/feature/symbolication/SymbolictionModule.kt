package ru.workinprogress.feature.symbolication

import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.symbolication.data.JVMFileStorage
import ru.workinprogress.feature.symbolication.data.SymbolMapRepositoryImpl

val symbolicationModule =
    module {
        single<Symbolicator>(named("android")) { AndroidR8Symbolicator() }
        singleOf(::SymbolMapRepositoryImpl).bind<SymbolMapRepository>()
        singleOf(::JVMFileStorage).bind<FileStorage>()

        single {
            SymbolicationService(
                symbolMapRepository = get(),
                fileStorage = get(),
                strategies =
                    mapOf(
                        MappingType.ANDROID_PROGUARD to get(named("android")),
                    ),
            )
        }
    }
