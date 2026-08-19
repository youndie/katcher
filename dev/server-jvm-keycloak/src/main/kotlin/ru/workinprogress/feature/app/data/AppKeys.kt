package ru.workinprogress.feature.app.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object AppKeys : LongIdTable("app_keys") {
    val appId = reference("app_id", Apps)
    val apiKey = varchar("api_key", 64).uniqueIndex()
    val createdAt = long("created_at")
    val lastUsedAt = long("last_used_at").nullable()
    val revokedAt = long("revoked_at").nullable()
}
