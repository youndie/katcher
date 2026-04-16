package ru.workinprogress.katcher.data

import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.smyrgeorge.sqlx4k.sqlite.SQLite
import ru.workinprogress.katcher.db.migrateDb

abstract class RepositoryTest {
    protected val db = SQLite(url = "sqlite::memory:")

    protected suspend fun setupSchema() {
        TransactionContext.withCurrent(db) {
            db.migrateDb()
        }
    }
}
