package io.github.youndie.katcher.feature.error.data

import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import io.github.youndie.katcher.feature.user.data.Users

object UserErrorGroupViewed : CompositeIdTable("user_error_group_viewed") {
    val groupId = reference("group_id", ErrorGroups)
    val userId = reference("user_id", Users)
    val viewedAt = long("viewed_at")

    override val primaryKey = PrimaryKey(groupId, userId)
}
