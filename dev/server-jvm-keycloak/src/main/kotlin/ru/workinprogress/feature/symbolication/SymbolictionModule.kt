package ru.workinprogress.feature.symbolication

import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.symbolication.data.SymbolMapRepositoryImpl
import ru.workinprogress.retrace.MappingFileStorage
import ru.workinprogress.retrace.MappingFileStorageOkio

val symbolicationModule =
    module {
        single<Symbolicator>(named("android")) { AndroidR8Symbolicator() }
        singleOf(::SymbolMapRepositoryImpl).bind<SymbolMapRepository>()
        single<MappingFileStorage> { MappingFileStorageOkio }
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
