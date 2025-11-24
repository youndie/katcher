package ru.workinprogress.feature.app.data

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Table("apps")
data class AppDb(
    @Id(insert = false)
    val id: Int,
    val name: String,
    val apiKey: String,
    val type: String,
)

object AppRowMapper : RowMapper<AppDb> {
    override fun map(row: ResultSet.Row): AppDb {
        val id: ResultSet.Row.Column = row.get("id")
        val name: ResultSet.Row.Column = row.get("name")
        val apiKey: ResultSet.Row.Column = row.get("api_key")
        val type: ResultSet.Row.Column = row.get("type")

        return AppDb(id = id.asInt(), name = name.asString(), apiKey = apiKey.asString(), type = type.asString())
    }
}

fun AppDb.toDomain() = App(id = id, name = name, apiKey = apiKey, type = AppType.valueOf(type))

@OptIn(ExperimentalUuidApi::class)
class AppRepositoryImpl(
    private val db: ISQLite,
    private val crudRepository: AppsCrudRepository,
) : AppRepository {
    override suspend fun create(
        name: String,
        type: AppType,
    ): App =
        TransactionContext.new(db) {
            val apiKey = Uuid.random().toString().replace("-", "")

            crudRepository
                .insert(db, AppDb(id = 0, name = name, apiKey = apiKey, type = type.name))
                .getOrThrow()
                .toDomain()
        }

    override suspend fun findByApiKey(apiKey: String): App? =
        TransactionContext.new(db) {
            crudRepository
                .findOneByApiKey(db, apiKey)
                .getOrNull()
                ?.toDomain()
        }

    override suspend fun findAll(): List<App> =
        TransactionContext.new(db) {
            crudRepository.findAll(db).getOrThrow().map { it.toDomain() }
        }

    override suspend fun findById(id: Int): App? =
        TransactionContext.new(db) {
            crudRepository.findOneById(db, id).getOrNull()?.toDomain()
        }
}

@Repository(mapper = AppRowMapper::class)
interface AppsCrudRepository : CrudRepository<AppDb> {
    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun findOneById(
        context: QueryExecutor,
        id: Int,
    ): Result<AppDb?>

    @Query("SELECT * FROM apps WHERE api_key = :apiKey")
    suspend fun findOneByApiKey(
        context: QueryExecutor,
        apiKey: String,
    ): Result<AppDb?>

    @Query("SELECT * FROM apps")
    suspend fun findAll(context: QueryExecutor): Result<List<AppDb>>
}
