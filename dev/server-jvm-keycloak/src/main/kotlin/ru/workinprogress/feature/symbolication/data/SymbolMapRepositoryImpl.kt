package ru.workinprogress.feature.symbolication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.workinprogress.feature.symbolication.SymbolMap
import ru.workinprogress.feature.symbolication.SymbolMapRepository

class SymbolMapRepositoryImpl : SymbolMapRepository {
    override suspend fun save(symbolMap: SymbolMap): Long =
        withContext(Dispatchers.IO) {
            transaction {
                SymbolMaps
                    .insertAndGetId {
                        it[appId] = symbolMap.appId
                        it[buildUuid] = symbolMap.buildUuid
                        it[mapType] = symbolMap.type
                        it[filePath] = symbolMap.filePath
                        it[versionName] = symbolMap.versionName
                        it[createdAt] = symbolMap.createdAt
                    }.value
            }
        }

    override suspend fun find(
        appId: Int,
        buildUuid: String,
    ): SymbolMap? =
        withContext(Dispatchers.IO) {
            transaction {
                SymbolMaps
                    .selectAll()
                    .where { (SymbolMaps.appId eq appId) and (SymbolMaps.buildUuid eq buildUuid) }
                    .limit(1)
                    .map { row ->
                        SymbolMap(
                            id = row[SymbolMaps.id].value,
                            appId = appId,
                            buildUuid = row[SymbolMaps.buildUuid],
                            type = row[SymbolMaps.mapType],
                            filePath = row[SymbolMaps.filePath],
                            versionName = row[SymbolMaps.versionName],
                            createdAt = row[SymbolMaps.createdAt],
                        )
                    }.singleOrNull()
            }
        }
}
