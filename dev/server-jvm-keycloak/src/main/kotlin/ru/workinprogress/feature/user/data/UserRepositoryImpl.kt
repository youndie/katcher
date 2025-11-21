package ru.workinprogress.feature.user.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.workinprogress.feature.user.User
import ru.workinprogress.feature.user.UserRepository

class UserRepositoryImpl : UserRepository {
    override suspend fun create(
        email: String,
        name: String,
    ): User =
        withContext(Dispatchers.IO) {
            transaction {
                val id =
                    Users
                        .insertAndGetId {
                            it[Users.email] = email
                            it[Users.name] = name
                        }.value

                User(id, email, name)
            }
        }

    override suspend fun findByEmail(email: String): User? {
        return withContext(Dispatchers.IO) {
            transaction {
                val row = Users.selectAll().where { Users.email eq email }.singleOrNull() ?: return@transaction null
                rowToUser(row)
            }
        }
    }

    override suspend fun findById(id: Int): User? =
        withContext(Dispatchers.IO) {
            transaction {
                val row = Users.selectAll().where { Users.id eq id }.singleOrNull() ?: return@transaction null
                rowToUser(row)
            }
        }

    private fun rowToUser(row: ResultRow): User? =
        User(
            id = row[Users.id].value,
            email = row[Users.email],
            name = row[Users.name],
        )
}
