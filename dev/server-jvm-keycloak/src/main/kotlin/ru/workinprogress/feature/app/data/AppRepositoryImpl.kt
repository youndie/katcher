package ru.workinprogress.feature.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppContents
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.error.data.ErrorGroups
import ru.workinprogress.feature.error.data.UserErrorGroupViewed
import ru.workinprogress.feature.report.data.Reports
import ru.workinprogress.feature.symbolication.data.SymbolMaps

class AppRepositoryImpl : AppRepository {
    override suspend fun create(
        name: String,
        type: AppType,
    ): App =
        withContext(Dispatchers.IO) {
            transaction {
                val id =
                    Apps
                        .insertAndGetId {
                            it[Apps.name] = name
                            it[Apps.type] = type.name
                        }.value

                App(id, name, type)
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

    override suspend fun rename(
        id: Int,
        name: String,
    ) {
        withContext(Dispatchers.IO) {
            transaction {
                Apps.update({ Apps.id eq id }) { it[Apps.name] = name }
            }
        }
    }

    override suspend fun delete(id: Int) {
        withContext(Dispatchers.IO) {
            transaction {
                // Same order as the native implementation: the foreign keys are RESTRICT, so
                // each table has to be empty before the one it points at.
                UserErrorGroupViewed.deleteWhere {
                    groupId inSubQuery ErrorGroups.select(ErrorGroups.id).where { ErrorGroups.appId eq id }
                }
                Reports.deleteWhere { appId eq id }
                ErrorGroups.deleteWhere { appId eq id }
                SymbolMaps.deleteWhere { appId eq id }
                AppKeys.deleteWhere { appId eq id }
                Apps.deleteWhere { Apps.id eq id }
            }
        }
    }

    override suspend fun contents(id: Int): AppContents =
        withContext(Dispatchers.IO) {
            transaction {
                AppContents(
                    groups =
                        ErrorGroups
                            .selectAll()
                            .where { ErrorGroups.appId eq id }
                            .count()
                            .toInt(),
                    reports =
                        Reports
                            .selectAll()
                            .where { Reports.appId eq id }
                            .count()
                            .toInt(),
                )
            }
        }

    private fun rowToApp(row: ResultRow): App? =
        try {
            App(
                id = row[Apps.id].value,
                name = row[Apps.name],
                type = AppType.valueOf(row[Apps.type]),
            )
        } catch (e: Exception) {
            null
        }
}
