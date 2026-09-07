package io.github.youndie.katcher.feature.user

import io.github.youndie.katcher.feature.user.data.UserRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val userModule =
    module {
        singleOf(::UserRepositoryImpl).bind<UserRepository>()
    }
