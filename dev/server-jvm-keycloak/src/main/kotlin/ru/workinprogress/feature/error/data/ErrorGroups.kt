package ru.workinprogress.feature.error.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import ru.workinprogress.feature.app.data.Apps

object ErrorGroups : LongIdTable("error_groups") {
    val appId = reference("app_id", Apps)
    val fingerprint = varchar("fingerprint", 255)
    val title = varchar("title", 255)
    val occurrences = integer("occurrences")
    val firstSeen = long("first_seen")
    val lastSeen = long("last_seen")
    val resolved = bool("resolved").default(false)

    // Mirrors migration V3 of the native server: the pull request an agent reported as
    // fixing this group, and when it said so.
    val fixUrl = text("fix_url").nullable()
    val fixLinkedAt = long("fix_linked_at").nullable()

    // Migration V5: the composed title, and the release a resolved group came back in.
    val exceptionType = text("exception_type").nullable()
    val message = text("message").nullable()
    val location = text("location").nullable()
    val regressedAt = long("regressed_at").nullable()
    val regressedRelease = text("regressed_release").nullable()

    init {
        index(isUnique = true, appId, fingerprint)
        index(false, lastSeen)
        index(false, resolved, lastSeen)
    }
}
