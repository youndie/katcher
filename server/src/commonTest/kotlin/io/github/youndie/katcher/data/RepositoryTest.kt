package io.github.youndie.katcher.data

import io.github.smyrgeorge.sqlx4k.impl.coroutines.TransactionContext
import io.github.smyrgeorge.sqlx4k.sqlite.SQLite
import io.github.youndie.katcher.db.migrateDb
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.test.AfterTest

/*
 * A FILE, NOT `:memory:`, AND THE JVM IS WHY.
 *
 * These tests ran on the native target alone until they were moved to `commonTest`, and the move is
 * what surfaced it. sqlx4k is two drivers — the Rust one on Kotlin/Native and Xerial's
 * `sqlite-jdbc` on the JVM — and on `:memory:` they disagree twice:
 *
 *   - the JVM driver REFUSES a pool larger than one, and it is right to: a second connection to
 *     `:memory:` is a second, empty database, so the default pool made every test here depend on
 *     which connection it happened to get. The native driver said nothing, and these tests never
 *     noticed, because none of them writes and reads through different connections;
 *   - pinning the pool to one then DEADLOCKS. Every test hung and failed after a minute with
 *     `UncompletedCoroutinesError`, because the transaction holds the only connection while the
 *     code inside it asks for another.
 *
 * So in-memory is not available on the JVM half at all with this driver. A temporary file is, it
 * takes the pool the production code takes, and it is what metrik's route tests already do.
 */
abstract class RepositoryTest {
    private val dbPath = "/tmp/katcher-test-${this::class.simpleName}.db"

    protected val db = SQLite(url = "sqlite:$dbPath")

    @AfterTest
    fun removeTheDatabaseFile() {
        FileSystem.SYSTEM.delete(dbPath.toPath(), mustExist = false)
    }

    protected suspend fun setupSchema() {
        TransactionContext.withCurrent(db) {
            db.migrateDb()
        }
    }
}
