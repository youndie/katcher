package ru.workinprogress.feature.report.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import ru.workinprogress.feature.app.data.Apps
import ru.workinprogress.feature.error.data.ErrorGroups

object Reports : LongIdTable("reports") {
    val appId = reference("app_id", Apps)
    val groupId = reference("group_id", ErrorGroups)
    val message = text("message")
    val stacktrace = text("stacktrace")
    val timestamp = long("timestamp")
    val context = text("context").nullable()
    val release = varchar("release", 64).nullable()
    val environment = varchar("environment", 64).nullable()
}
