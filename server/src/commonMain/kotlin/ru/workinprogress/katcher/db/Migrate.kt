package ru.workinprogress.katcher.db

val commands =
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
    )
