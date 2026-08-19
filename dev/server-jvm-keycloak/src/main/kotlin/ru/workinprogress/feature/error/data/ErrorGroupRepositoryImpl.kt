@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.feature.error.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.workinprogress.feature.error.CreateErrorGroupParams
import ru.workinprogress.feature.error.DuplicateErrorGroupException
import ru.workinprogress.feature.error.ErrorGroup
import ru.workinprogress.feature.error.ErrorGroupFilterOptions
import ru.workinprogress.feature.error.ErrorGroupRepository
import ru.workinprogress.feature.error.ErrorGroupWithViewed
import ru.workinprogress.feature.error.ErrorGroupsPaginated
import ru.workinprogress.feature.report.ErrorGroupFilter
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder
import ru.workinprogress.feature.report.data.Reports
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

    override suspend fun reopen(groupId: Long) {
        withContext(Dispatchers.IO) {
            transaction {
                ErrorGroups.update({ ErrorGroups.id eq groupId }) {
                    it[resolved] = false
                }
            }
        }
    }

    override suspend fun markRegressed(
        groupId: Long,
        release: String?,
        at: Long,
    ) {
        withContext(Dispatchers.IO) {
            transaction {
                ErrorGroups.update({ ErrorGroups.id eq groupId }) {
                    it[resolved] = false
                    it[regressedAt] = at
                    it[regressedRelease] = release
                }
            }
        }
    }

    override suspend fun linkFix(
        groupId: Long,
        fixUrl: String,
        linkedAt: Long,
    ) {
        withContext(Dispatchers.IO) {
            transaction {
                ErrorGroups.update({ ErrorGroups.id eq groupId }) {
                    it[ErrorGroups.fixUrl] = fixUrl
                    it[fixLinkedAt] = linkedAt
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
                            it[occurrences] = 0
                            it[lastSeen] = Clock.System.now().toEpochMilliseconds()
                            it[firstSeen] = Clock.System.now().toEpochMilliseconds()
                            it[exceptionType] = newGroup.exceptionType
                            it[message] = newGroup.message
                            it[location] = newGroup.location
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
        filter: ErrorGroupFilter,
        now: Long,
    ): ErrorGroupsPaginated =
        withContext(Dispatchers.IO) {
            transaction {
                fun Op<Boolean>.withFilters(): Op<Boolean> {
                    var condition = this
                    if (filter.unresolvedOnly) condition = condition and (ErrorGroups.resolved eq false)
                    filter.days?.let { days ->
                        condition = condition and (ErrorGroups.lastSeen greaterEq (now - days * DAY_MILLIS))
                    }
                    filter.query?.takeIf { it.isNotBlank() }?.let { query ->
                        val pattern = "%" + query.lowercase() + "%"
                        condition =
                            condition and
                            (
                                ErrorGroups.exceptionType.lowerCase().like(pattern) or
                                    ErrorGroups.message.lowerCase().like(pattern) or
                                    ErrorGroups.location.lowerCase().like(pattern) or
                                    ErrorGroups.title.lowerCase().like(pattern)
                            )
                    }
                    filter.environment?.let { environment ->
                        condition =
                            condition and
                            exists(
                                Reports.selectAll().where {
                                    (Reports.groupId eq ErrorGroups.id) and (Reports.environment eq environment)
                                },
                            )
                    }
                    filter.release?.let { release ->
                        condition =
                            condition and
                            exists(
                                Reports.selectAll().where {
                                    (Reports.groupId eq ErrorGroups.id) and (Reports.release eq release)
                                },
                            )
                    }
                    return condition
                }

                val totalUnfiltered =
                    ErrorGroups
                        .selectAll()
                        .where { ErrorGroups.appId eq appId }
                        .count()
                        .toInt()

                val total =
                    ErrorGroups
                        .selectAll()
                        .where { (ErrorGroups.appId eq appId).withFilters() }
                        .count()
                        .toInt()

                val items =
                    ErrorGroups
                        .leftJoin(UserErrorGroupViewed, { ErrorGroups.id }, { UserErrorGroupViewed.groupId })
                        .select(ErrorGroups.columns + listOf(UserErrorGroupViewed.viewedAt))
                        .where { (ErrorGroups.appId eq appId).withFilters() }
                        .orderBy(
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
                    total = total,
                    totalUnfiltered = totalUnfiltered,
                    filter = filter,
                )
            }
        }

    override suspend fun filterOptions(appId: Int): ErrorGroupFilterOptions =
        withContext(Dispatchers.IO) {
            transaction {
                fun distinct(column: Column<String?>): List<String> =
                    Reports
                        .select(column)
                        .where { (Reports.appId eq appId) and column.isNotNull() }
                        .withDistinct()
                        .limit(MAX_FILTER_OPTIONS)
                        .mapNotNull { row -> row[column] }
                        .sortedDescending()

                ErrorGroupFilterOptions(
                    environments = distinct(Reports.environment),
                    releases = distinct(Reports.release),
                )
            }
        }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_FILTER_OPTIONS = 20
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
            fixUrl = row[ErrorGroups.fixUrl],
            exceptionType = row[ErrorGroups.exceptionType],
            message = row[ErrorGroups.message],
            location = row[ErrorGroups.location],
            regressedAt = row[ErrorGroups.regressedAt],
            regressedRelease = row[ErrorGroups.regressedRelease],
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
