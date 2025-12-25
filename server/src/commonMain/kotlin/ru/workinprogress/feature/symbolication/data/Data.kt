package ru.workinprogress.feature.symbolication.data

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
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import ru.workinprogress.feature.symbolication.MappingType
import ru.workinprogress.feature.symbolication.SymbolMap
import ru.workinprogress.feature.symbolication.SymbolMapRepository

object SymbolMapRowMapper : RowMapper<SymbolMapDb> {
    override fun map(row: ResultSet.Row): SymbolMapDb {
        val id: ResultSet.Row.Column = row.get("id")
        val appId: ResultSet.Row.Column = row.get("app_id")
        val buildUuid: ResultSet.Row.Column = row.get("build_uuid")
        val type: ResultSet.Row.Column = row.get("map_type")
        val filePath: ResultSet.Row.Column = row.get("file_path")
        val versionName: ResultSet.Row.Column = row.get("version_name")
        val createdAt: ResultSet.Row.Column = row.get("created_at")

        return SymbolMapDb(
            id = id.asLong(),
            appId = appId.asInt(),
            buildUuid = buildUuid.asString(),
            mapType = type.asString(),
            filePath = filePath.asString(),
            versionName = versionName.asString(),
            createdAt = createdAt.asLong(),
        )
    }
}

@Table("symbol_maps")
data class SymbolMapDb(
    @Id(insert = false)
    val id: Long,
    val appId: Int,
    val buildUuid: String,
    val mapType: String,
    val filePath: String,
    val versionName: String,
    val createdAt: Long,
)

fun SymbolMapDb.toDomain() =
    SymbolMap(
        id = id,
        appId = appId,
        buildUuid = buildUuid,
        type = MappingType.valueOf(mapType),
        filePath = filePath,
        versionName = versionName,
        createdAt = createdAt,
    )

fun SymbolMap.toDb() =
    SymbolMapDb(
        id = id,
        appId = appId,
        buildUuid = buildUuid,
        mapType = type.name,
        filePath = filePath,
        versionName = versionName.orEmpty(),
        createdAt = createdAt,
    )

@Repository(SymbolMapRowMapper::class)
interface SymbolMapCrudRepository : CrudRepository<SymbolMapDb> {
    @Query("SELECT * FROM symbol_maps WHERE app_id = :appId AND build_uuid = :buildUuid LIMIT 1")
    suspend fun findOneByAppIdAndBuildUuid(
        context: QueryExecutor,
        appId: Int,
        buildUuid: String,
    ): Result<SymbolMapDb?>
}

class SymbolMapRepositoryImpl(
    private val db: ISQLite,
    private val crudRepository: SymbolMapCrudRepository,
) : SymbolMapRepository {
    override suspend fun find(
        appId: Int,
        buildUuid: String,
    ): SymbolMap? =
        TransactionContext.withCurrent(db) {
            crudRepository.findOneByAppIdAndBuildUuid(this, appId, buildUuid).getOrNull()?.toDomain()
        }

    override suspend fun save(symbolMap: SymbolMap): Long =
        TransactionContext.withCurrent(db) {
            crudRepository
                .insert(this, symbolMap.toDb())
                .onFailure { println(it.message.orEmpty()) }
                .getOrNull()
                ?.id ?: 0L
        }
}
