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

    init {
        index(isUnique = true, appId, fingerprint)
        index(false, lastSeen)
        index(false, resolved, lastSeen)
    }
}
