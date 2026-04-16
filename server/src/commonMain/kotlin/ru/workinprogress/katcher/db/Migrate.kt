package ru.workinprogress.katcher.db

import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite

private val initial =
    listOf(
        """CREATE TABLE users (
id INTEGER PRIMARY KEY AUTOINCREMENT,
email TEXT NOT NULL,
name TEXT NOT NULL
);""",
        """CREATE UNIQUE INDEX users_email ON users(email);""",
        """CREATE TABLE apps (
id INTEGER PRIMARY KEY AUTOINCREMENT,
name TEXT NOT NULL,
api_key TEXT NOT NULL,
type TEXT NOT NULL
);""",
        """CREATE UNIQUE INDEX apps_api_key ON apps(api_key);""",
        """CREATE TABLE error_groups (
id INTEGER PRIMARY KEY AUTOINCREMENT,
app_id INTEGER NOT NULL,
fingerprint TEXT NOT NULL,
title TEXT NOT NULL,
occurrences INTEGER NOT NULL,
first_seen BIGINT NOT NULL,
last_seen BIGINT NOT NULL,
resolved BOOLEAN NOT NULL DEFAULT 0,
FOREIGN KEY (app_id) REFERENCES apps(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);""",
        """CREATE UNIQUE INDEX error_groups_app_id_fingerprint
ON error_groups (app_id, fingerprint);""",
        """CREATE INDEX error_groups_last_seen
ON error_groups (last_seen);""",
        """CREATE INDEX error_groups_resolved_last_seen
ON error_groups (resolved, last_seen);""",
        """CREATE TABLE reports (
id INTEGER PRIMARY KEY AUTOINCREMENT,
app_id INTEGER NOT NULL,
group_id INTEGER NOT NULL,
message TEXT NOT NULL,
stacktrace TEXT NOT NULL,
timestamp BIGINT NOT NULL,
context TEXT,
release TEXT,
environment TEXT,
FOREIGN KEY (app_id) REFERENCES apps(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
FOREIGN KEY (group_id) REFERENCES error_groups(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);""",
        """CREATE TABLE user_error_group_viewed (
group_id INTEGER NOT NULL,
user_id INTEGER NOT NULL,
viewed_at BIGINT NOT NULL,
PRIMARY KEY (group_id, user_id),
FOREIGN KEY (group_id) REFERENCES error_groups(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);""",
        """CREATE TABLE IF NOT EXISTS symbol_maps (
id INTEGER PRIMARY KEY AUTOINCREMENT,
app_id INTEGER NOT NULL,
build_uuid TEXT NOT NULL,
map_type TEXT NOT NULL,
file_path TEXT NOT NULL,
version_name TEXT,
created_at INTEGER NOT NULL,
FOREIGN KEY(app_id) REFERENCES apps(id) ON DELETE CASCADE
);""",
        """CREATE INDEX IF NOT EXISTS idx_symbol_maps_lookup ON symbol_maps(app_id, build_uuid);""",
    )

private val migrationV1 = initial

private val migrationV2 = listOf(
    """ALTER TABLE reports ADD COLUMN breadcrumbs TEXT NULL;"""
)

val allMigrations = listOf(
    migrationV1,
    migrationV2
)

suspend fun ISQLite.migrateDb() {
    this.transaction {
        var currentVersion =
            fetchAll("PRAGMA user_version = 0;").getOrNull()?.rows?.getOrNull(0)?.get(0)?.asLong()?.toInt() ?: 0

        if (currentVersion == 0) {
            val tablesExist = fetchAll("SELECT name FROM sqlite_master WHERE type='table' AND name='users';")
                .getOrNull()?.rows?.getOrNull(0)?.get(0) != null

            if (tablesExist) {
                execute("PRAGMA user_version = 1;")
                currentVersion = 1
                println("Detected legacy database. Version set to 1.")
            }
        }

        val targetVersion = allMigrations.size

        if (currentVersion < targetVersion) {
            for (v in (currentVersion + 1)..targetVersion) {
                val migration = allMigrations[v - 1]
                migration.forEach { sql ->
                    this.execute(sql)
                }
                this.execute("PRAGMA user_version = $v;")
                println("Migrated to version $v")
            }
        }
    }
}
