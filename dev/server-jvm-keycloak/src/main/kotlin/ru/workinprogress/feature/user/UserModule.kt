package ru.workinprogress.feature.user

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.user.data.UserRepositoryImpl

val userModule =
    module {
        singleOf(::UserRepositoryImpl).bind<UserRepository>()
    }
