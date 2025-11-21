package ru.workinprogress.katcher.data

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.workinprogress.feature.app.data.Apps
import ru.workinprogress.feature.error.data.ErrorGroups
import ru.workinprogress.feature.error.data.UserErrorGroupViewed
import ru.workinprogress.feature.report.data.Reports
import ru.workinprogress.feature.user.data.Users
import java.io.File

fun initDatabase() {
    val dbPath = runCatching { System.getenv("DB_PATH") }.getOrNull() ?: "/data/local.db"
    val file = File(dbPath)

    if (!file.exists()) {
        file.parentFile.mkdirs()
    }

    val dbFilePath = file.absolutePath
    println("Attempting to connect to database at: $dbFilePath")

    Database.connect(
        url = "jdbc:sqlite:$dbFilePath",
        driver = "org.sqlite.JDBC",
    )

    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.createMissingTablesAndColumns(Users, Apps, Reports, ErrorGroups, UserErrorGroupViewed)
    }
}
