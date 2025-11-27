@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.error.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.DuplicateErrorGroupException
import ru.workinprogress.feature.error.ErrorGroup
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ErrorGroupWithViewed
import ru.workinprogress.feature.error.ErrorGroupsPaginated
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ErrorGroupRepositoryImpl : ErrorGroupRepository {
    override suspend fun findById(groupId: Long): ErrorGroup? =
        withContext(Dispatchers.IO) {
            transaction {
                ErrorGroups
                    .selectAll()
                    .where { ErrorGroups.id eq groupId }
                    .map { rowToErrorGroup(it) }
                    .singleOrNull()
            }
        }

    override suspend fun findByFingerprint(
        appId: Int,
        fingerprint: String,
    ): ErrorGroup? =
        withContext(Dispatchers.IO) {
            transaction {
                ErrorGroups
                    .selectAll()
                    .where {
                        (ErrorGroups.appId eq appId) and (ErrorGroups.fingerprint eq fingerprint)
                    }.map { rowToErrorGroup(it) }
                    .singleOrNull()
            }
        }

    override suspend fun updateOccurrences(id: Long) {
        withContext(Dispatchers.IO) {
            transaction {
                val currentCount =
                    ErrorGroups
                        .selectAll()
                        .where { ErrorGroups.id eq id }
                        .singleOrNull()
                        ?.getOrNull(ErrorGroups.occurrences) ?: 0

                ErrorGroups.update({ ErrorGroups.id eq id }) {
                    it[ErrorGroups.occurrences] = currentCount + 1
                    it[ErrorGroups.lastSeen] = Clock.System.now().toEpochMilliseconds()
                }
            }
        }
    }

    override suspend fun resolve(groupId: Long) {
        withContext(Dispatchers.IO) {
            transaction {
                ErrorGroups.update({ ErrorGroups.id eq groupId }) {
                    it[resolved] = true
                }
            }
        }
    }

    override suspend fun insert(newGroup: CreateErrorGroupParams): ErrorGroup =
        runCatching {
            withContext(Dispatchers.IO) {
                transaction {
                    val id =
                        ErrorGroups.insertAndGetId {
                            it[appId] = newGroup.appId
                            it[fingerprint] = newGroup.fingerprint
                            it[title] = newGroup.title
                            it[occurrences] = 1
                            it[lastSeen] = Clock.System.now().toEpochMilliseconds()
                            it[firstSeen] = Clock.System.now().toEpochMilliseconds()
                        }

                    ErrorGroups
                        .selectAll()
                        .where { ErrorGroups.id eq id }
                        .single()
                        .let { rowToErrorGroup(it) }
                }
            }
        }.onFailure { e ->
            if (e.message?.contains("duplicate key") == true) {
                throw DuplicateErrorGroupException(e.message.orEmpty())
            } else {
                throw e
            }
        }.getOrThrow()

    override suspend fun findByAppId(
        appId: Int,
        userId: Int,
        page: Int,
        pageSize: Int,
        sortBy: ErrorGroupSort,
        sortOrder: ErrorGroupSortOrder,
    ): ErrorGroupsPaginated =
        withContext(Dispatchers.IO) {
            transaction {
                val total =
                    ErrorGroups
                        .selectAll()
                        .where { ErrorGroups.appId eq appId }
                        .count()
                        .toInt()

                val items =
                    ErrorGroups
                        .leftJoin(UserErrorGroupViewed, { ErrorGroups.id }, { UserErrorGroupViewed.groupId })
                        .select(ErrorGroups.columns + listOf(UserErrorGroupViewed.viewedAt))
                        .where {
                            (ErrorGroups.appId eq appId)
                        }.orderBy(
                            when (sortBy) {
                                ErrorGroupSort.id -> ErrorGroups.id
                                ErrorGroupSort.title -> ErrorGroups.title
                                ErrorGroupSort.lastSeen -> ErrorGroups.lastSeen
                                ErrorGroupSort.occurrences -> ErrorGroups.occurrences
                            },
                            if (sortOrder == ErrorGroupSortOrder.asc) SortOrder.ASC else SortOrder.DESC,
                        ).offset(pageSize * (page.toLong() - 1))
                        .limit(pageSize)
                        .map { rowToErrorGroupViewed(it) }

                ErrorGroupsPaginated(
                    items = items,
                    page = page,
                    totalPages = (total + pageSize - 1) / pageSize,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                )
            }
        }

    private fun rowToErrorGroup(row: ResultRow) =
        ErrorGroup(
            id = row[ErrorGroups.id].value,
            appId = row[ErrorGroups.appId].value,
            fingerprint = row[ErrorGroups.fingerprint],
            title = row[ErrorGroups.title],
            occurrences = row[ErrorGroups.occurrences],
            firstSeen =
                Instant
                    .fromEpochMilliseconds(row[ErrorGroups.firstSeen])
                    .toLocalDateTime(TimeZone.currentSystemDefault()),
            lastSeen =
                Instant
                    .fromEpochMilliseconds(row[ErrorGroups.lastSeen])
                    .toLocalDateTime(TimeZone.currentSystemDefault()),
            resolved = row[ErrorGroups.resolved],
        )

    private fun rowToErrorGroupViewed(row: ResultRow) =
        ErrorGroupWithViewed(
            rowToErrorGroup(row),
            row
                .getOrNull(UserErrorGroupViewed.viewedAt)
                ?.let {
                    Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault())
                }?.let { viewedAt ->
                    Instant
                        .fromEpochMilliseconds(row[ErrorGroups.lastSeen])
                        .toLocalDateTime(TimeZone.currentSystemDefault()) < viewedAt
                } ?: false,
        )
}
