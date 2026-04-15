package ru.workinprogress.feature.symbolication.data

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import ru.workinprogress.feature.symbolication.MappingType
import ru.workinprogress.feature.symbolication.SymbolMap
import ru.workinprogress.feature.symbolication.SymbolMapRepository
import ru.workinprogress.katcher.db.SymbolMapDbAutoRowMapper

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

@Repository(SymbolMapDbAutoRowMapper::class)
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
