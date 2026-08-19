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

private val migrationV2 =
    listOf(
        """ALTER TABLE reports ADD COLUMN breadcrumbs TEXT NULL;""",
    )

private val migrationV3 =
    listOf(
        """ALTER TABLE error_groups ADD COLUMN fix_url TEXT NULL;""",
        """ALTER TABLE error_groups ADD COLUMN fix_linked_at BIGINT NULL;""",
    )

private val migrationV4 =
    listOf(
        // The apps list counts crashes per day per app on every render; without this the
        // count is a full scan of reports, the largest table there is.
        """CREATE INDEX IF NOT EXISTS idx_reports_app_timestamp ON reports(app_id, timestamp);""",
    )

private val migrationV5 =
    listOf(
        // The composed title: three fields instead of a truncated head of the stacktrace.
        // Nullable on purpose — groups created before this keep falling back to `title`.
        """ALTER TABLE error_groups ADD COLUMN exception_type TEXT NULL;""",
        """ALTER TABLE error_groups ADD COLUMN message TEXT NULL;""",
        """ALTER TABLE error_groups ADD COLUMN location TEXT NULL;""",
        """ALTER TABLE error_groups ADD COLUMN regressed_at BIGINT NULL;""",
        """ALTER TABLE error_groups ADD COLUMN regressed_release TEXT NULL;""",
    )

private val migrationV6 =
    listOf(
        // The environment and release filters ask "does this group have a report like that",
        // which without these is a scan of every report the group ever had.
        """CREATE INDEX IF NOT EXISTS idx_reports_group_environment ON reports(group_id, environment);""",
        """CREATE INDEX IF NOT EXISTS idx_reports_group_release ON reports(group_id, release);""",
    )

private val migrationV7 =
    listOf(
        // Keys move out of the apps row: an app can have more than one alive so a reissue does
        // not cut off builds already in the field, and each key remembers when it was last
        // used — which is what makes revoking the old one a decision rather than a guess.
        """CREATE TABLE app_keys (
id INTEGER PRIMARY KEY AUTOINCREMENT,
app_id INTEGER NOT NULL,
api_key TEXT NOT NULL,
created_at BIGINT NOT NULL,
last_used_at BIGINT NULL,
revoked_at BIGINT NULL,
FOREIGN KEY (app_id) REFERENCES apps(id) ON DELETE CASCADE
);""",
        """CREATE UNIQUE INDEX app_keys_api_key ON app_keys(api_key);""",
        """CREATE INDEX app_keys_app_id ON app_keys(app_id);""",
        // Zero rather than a made-up date: nobody recorded when these were issued.
        """INSERT INTO app_keys (app_id, api_key, created_at) SELECT id, api_key, 0 FROM apps;""",
        // The column goes, index first — SQLite refuses to drop an indexed column. Leaving a
        // second copy of every key in a table nothing writes to is how it goes stale in secret.
        """DROP INDEX apps_api_key;""",
        """ALTER TABLE apps DROP COLUMN api_key;""",
    )

val allMigrations =
    listOf(
        migrationV1,
        migrationV2,
        migrationV3,
        migrationV4,
        migrationV5,
        migrationV6,
        migrationV7,
    )

suspend fun ISQLite.migrateDb() {
    this.transaction {
        // `PRAGMA user_version = 0` is not a read — it writes zero. Asking that way reset the
        // version on every start, so every start decided the database was legacy and ran all
        // migrations again; the statements failed and nobody looked, because the results were
        // discarded too. The version is read here and every statement below is checked.
        var currentVersion =
            fetchAll("PRAGMA user_version;")
                .getOrThrow()
                .rows
                .firstOrNull()
                ?.get(0)
                ?.asLong()
                ?.toInt() ?: 0

        if (currentVersion == 0) {
            val tablesExist =
                fetchAll("SELECT name FROM sqlite_master WHERE type='table' AND name='users';")
                    .getOrThrow()
                    .rows
                    .firstOrNull()
                    ?.get(0) != null

            if (tablesExist) {
                execute("PRAGMA user_version = 1;").getOrThrow()
                currentVersion = 1
                println("Detected legacy database. Version set to 1.")
            }
        }

        val targetVersion = allMigrations.size

        if (currentVersion < targetVersion) {
            for (v in (currentVersion + 1)..targetVersion) {
                val migration = allMigrations[v - 1]
                migration.forEach { sql ->
                    // A migration that fails must stop the start, not be stepped over: a
                    // server that runs on a half-migrated schema breaks later, somewhere else,
                    // and by then nothing points back here.
                    this.execute(sql).getOrThrow()
                }
                this.execute("PRAGMA user_version = $v;").getOrThrow()
                println("Migrated to version $v")
            }
        }
    }
}
