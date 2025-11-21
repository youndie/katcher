package ru.workinprogress.feature.user

interface UserRepository {
    suspend fun create(
        email: String,
        name: String,
    ): User

    suspend fun findById(id: Int): User?

    suspend fun findByEmail(email: String): User?
}
