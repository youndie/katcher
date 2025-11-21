package ru.workinprogress.feature.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import java.util.UUID

class AppRepositoryImpl : AppRepository {
    override suspend fun create(
        name: String,
        type: AppType,
    ): App =
        withContext(Dispatchers.IO) {
            transaction {
                val apiKey = UUID.randomUUID().toString().replace("-", "")
                val id =
                    Apps
                        .insertAndGetId {
                            it[Apps.name] = name
                            it[Apps.type] = type.name
                            it[Apps.apiKey] = apiKey
                        }.value

                App(id, name, type, apiKey)
            }
        }

    override suspend fun findByApiKey(apiKey: String): App? =
        withContext(Dispatchers.IO) {
            transaction {
                Apps
                    .selectAll()
                    .where { Apps.apiKey eq apiKey }
                    .mapNotNull { rowToApp(it) }
                    .singleOrNull()
            }
        }

    override suspend fun findAll(): List<App> =
        withContext(Dispatchers.IO) {
            transaction {
                Apps
                    .selectAll()
                    .mapNotNull { rowToApp(it) }
            }
        }

    override suspend fun findById(id: Int): App? =
        withContext(Dispatchers.IO) {
            transaction {
                Apps
                    .selectAll()
                    .where { Apps.id eq id }
                    .mapNotNull { rowToApp(it) }
                    .singleOrNull()
            }
        }

    private fun rowToApp(row: ResultRow): App? =
        try {
            App(
                id = row[Apps.id].value,
                name = row[Apps.name],
                type = AppType.valueOf(row[Apps.type]),
                apiKey = row[Apps.apiKey],
            )
        } catch (e: Exception) {
            null
        }
}
