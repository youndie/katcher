package ru.workinprogress.feature.app.data

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object Apps : IntIdTable("apps") {
    val name = varchar("name", 255)
    val apiKey = varchar("api_key", 64).uniqueIndex()
    val type = varchar("type", 64)
}
