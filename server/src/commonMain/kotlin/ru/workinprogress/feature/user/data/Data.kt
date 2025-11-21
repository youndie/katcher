package ru.workinprogress.feature.user.data

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import ru.workinprogress.feature.user.User
import ru.workinprogress.feature.user.UserRepository

@Table("users")
data class UserDb(
    @Id(insert = false)
    val id: Int,
    val email: String,
    val name: String,
)

object UserRowMapper : RowMapper<UserDb> {
    override fun map(row: ResultSet.Row): UserDb = UserDb(row.get("id").asInt(), row.get("email").asString(), row.get("name").asString())
}

fun UserDb.toDomain() = User(id = id, email = email, name = name)

@Repository(mapper = UserRowMapper::class)
interface UsersCrudRepository : CrudRepository<UserDb> {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findOneById(
        context: QueryExecutor,
        id: Int,
    ): Result<UserDb?>

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun findOneByEmail(
        context: QueryExecutor,
        email: String,
    ): Result<UserDb?>
}

class UserRepositoryImpl(
    private val db: ISQLite,
    private val crudRepository: UsersCrudRepository,
) : UserRepository {
    override suspend fun create(
        email: String,
        name: String,
    ): User =
        db
            .transaction {
                crudRepository.insert(db, UserDb(0, email, name))
            }.getOrThrow()
            .toDomain()

    override suspend fun findById(id: Int): User? =
        db
            .transaction {
                crudRepository.findOneById(db, id).getOrNull()?.toDomain()
            }

    override suspend fun findByEmail(email: String): User? =
        db.transaction {
            crudRepository.findOneByEmail(db, email).getOrNull()?.toDomain()
        }
}
