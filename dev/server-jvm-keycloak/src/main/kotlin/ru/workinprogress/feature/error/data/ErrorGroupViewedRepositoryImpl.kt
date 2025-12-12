package ru.workinprogress.feature.error.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import ru.workinprogress.feature.error.ErrorGroupViewedRepository
import ru.workinprogress.feature.user.data.Users
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ErrorGroupViewedRepositoryImpl : ErrorGroupViewedRepository {
    override suspend fun updateVisitedAt(
        errorGroupId: Long,
        forUserId: Int,
    ) {
        withContext(Dispatchers.IO) {
            transaction {
                UserErrorGroupViewed.upsert {
                    it[groupId] = EntityID(errorGroupId, ErrorGroups)
                    it[userId] = EntityID(forUserId, Users)
                    it[viewedAt] = Clock.System.now().toEpochMilliseconds()
                }
            }
        }
    }

    override suspend fun removeVisits(errorGroupId: Long) {
        withContext(Dispatchers.IO) {
            transaction {
                UserErrorGroupViewed.deleteWhere { UserErrorGroupViewed.groupId eq errorGroupId }
            }
        }
    }
}
