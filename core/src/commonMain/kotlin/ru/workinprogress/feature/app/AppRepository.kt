package ru.workinprogress.feature.app

interface AppRepository {
    suspend fun create(
        name: String,
        type: AppType,
    ): App

    suspend fun findByApiKey(apiKey: String): App?

    suspend fun findAll(): List<App>

    suspend fun findById(id: Int): App?
}
