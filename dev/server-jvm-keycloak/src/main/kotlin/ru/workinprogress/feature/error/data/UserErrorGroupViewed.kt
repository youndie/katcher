package ru.workinprogress.feature.error.data

import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import ru.workinprogress.feature.user.data.Users

object UserErrorGroupViewed : CompositeIdTable("user_error_group_viewed") {
    val groupId = reference("group_id", ErrorGroups)
    val userId = reference("user_id", Users)
    val viewedAt = long("viewed_at")

    override val primaryKey = PrimaryKey(groupId, userId)
}
