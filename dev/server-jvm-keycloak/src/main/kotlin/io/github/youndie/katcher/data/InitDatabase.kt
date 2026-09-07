package io.github.youndie.katcher.data

import io.github.youndie.katcher.feature.app.data.Apps
import io.github.youndie.katcher.feature.error.data.ErrorGroups
import io.github.youndie.katcher.feature.error.data.UserErrorGroupViewed
import io.github.youndie.katcher.feature.report.data.Reports
import io.github.youndie.katcher.feature.symbolication.data.SymbolMaps
import io.github.youndie.katcher.feature.user.data.Users
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

fun initDatabase() {
    val dbPath = runCatching { System.getenv("DB_PATH") }.getOrNull() ?: "./data/local.db"
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
        SchemaUtils.createMissingTablesAndColumns(Users, Apps, Reports, ErrorGroups, UserErrorGroupViewed, SymbolMaps)
    }
}
