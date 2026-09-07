package io.github.youndie.katcher.feature.user

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import io.github.youndie.katcher.feature.user.data.UserRepositoryImpl

val userModule =
    module {
        singleOf(::UserRepositoryImpl).bind<UserRepository>()
    }
