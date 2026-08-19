package ru.workinprogress.feature.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.workinprogress.feature.app.AppKey
import ru.workinprogress.feature.app.AppKeyRepository
import java.util.UUID

class AppKeyRepositoryImpl : AppKeyRepository {
    override suspend fun findActiveByKey(key: String): AppKey? =
        withContext(Dispatchers.IO) {
            transaction {
                AppKeys
                    .selectAll()
                    .where { (AppKeys.apiKey eq key) and AppKeys.revokedAt.isNull() }
                    .limit(1)
                    .map { row -> row.toAppKey() }
                    .firstOrNull()
            }
        }

    override suspend fun listByApp(appId: Int): List<AppKey> =
        withContext(Dispatchers.IO) {
            transaction {
                AppKeys
                    .selectAll()
                    .where { AppKeys.appId eq appId }
                    .orderBy(AppKeys.id, SortOrder.DESC)
                    .map { row -> row.toAppKey() }
            }
        }

    override suspend fun listAll(): Map<Int, List<AppKey>> =
        withContext(Dispatchers.IO) {
            transaction {
                AppKeys
                    .selectAll()
                    .orderBy(AppKeys.id, SortOrder.DESC)
                    .map { row -> row.toAppKey() }
                    .groupBy { key -> key.appId }
            }
        }

    override suspend fun issue(
        appId: Int,
        at: Long,
    ): AppKey =
        withContext(Dispatchers.IO) {
            transaction {
                val key = UUID.randomUUID().toString().replace("-", "")
                val id =
                    AppKeys
                        .insertAndGetId {
                            it[AppKeys.appId] = appId
                            it[apiKey] = key
                            it[createdAt] = at
                        }.value

                AppKeys
                    .selectAll()
                    .where { AppKeys.id eq id }
                    .single()
                    .toAppKey()
            }
        }

    override suspend fun revoke(
        id: Long,
        at: Long,
    ) {
        withContext(Dispatchers.IO) {
            transaction {
                AppKeys.update({ (AppKeys.id eq id) and AppKeys.revokedAt.isNull() }) {
                    it[revokedAt] = at
                }
            }
        }
    }

    override suspend fun markUsed(
        id: Long,
        at: Long,
    ) {
        withContext(Dispatchers.IO) {
            transaction {
                AppKeys.update({ AppKeys.id eq id }) {
                    it[lastUsedAt] = at
                }
            }
        }
    }

    private fun ResultRow.toAppKey() =
        AppKey(
            id = this[AppKeys.id].value,
            appId = this[AppKeys.appId].value,
            key = this[AppKeys.apiKey],
            createdAt = this[AppKeys.createdAt],
            lastUsedAt = this[AppKeys.lastUsedAt],
            revokedAt = this[AppKeys.revokedAt],
        )
}
