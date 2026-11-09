package io.github.youndie.katcher.db

import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.youndie.katcher.feature.error.ProcessReportUseCase
import io.github.youndie.katcher.feature.report.Breadcrumb
import io.github.youndie.katcher.feature.report.CredentialMask
import io.github.youndie.katcher.feature.report.maskCredentials
import kotlinx.serialization.json.Json

/** Rows are read in pages by id, so a large `reports` table is never held in memory at once. */
private const val PAGE = 500

private class StoredReportRow(
    val id: Long,
    val message: String,
    val stacktrace: String,
    val context: String?,
    val breadcrumbs: String?,
)

private object StoredReportRowMapper : RowMapper<StoredReportRow> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ) = StoredReportRow(
        id = row.get("id").asLong(),
        message = row.get("message").asString(),
        stacktrace = row.get("stacktrace").asString(),
        context = row.get("context").asStringOrNull(),
        breadcrumbs = row.get("breadcrumbs").asStringOrNull(),
    )
}

private class StoredGroupRow(
    val id: Long,
    val appId: Int,
    val title: String,
    val message: String?,
)

private object StoredGroupRowMapper : RowMapper<StoredGroupRow> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ) = StoredGroupRow(
        id = row.get("id").asLong(),
        appId = row.get("app_id").asInt(),
        title = row.get("title").asString(),
        message = row.get("message").asStringOrNull(),
    )
}

private object StacktraceMapper : RowMapper<String> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ) = row.get("stacktrace").asString()
}

private object IdMapper : RowMapper<Long> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ) = row.get("id").asLong()
}

/**
 * Applies [CredentialMask] to what ingest stored before it masked: the text of every report and the
 * title and message of every group. A row the mask leaves as it was is not written, so a second run
 * changes nothing.
 *
 * A group whose title changed gets its fingerprint recomputed from its latest report, now masked.
 * Ingest computes the fingerprint from masked text, and a secret in the header can move it — URL
 * userinfo does — so without this the next report of an old failure would open a new group beside
 * the old one. Where another group of the app already holds the recomputed fingerprint the old one
 * is kept: two groups are not merged here.
 *
 * The secret itself is still to be treated as leaked and rotated by whoever owns it; this only stops
 * the pages and the MCP listing showing it.
 */
internal suspend fun QueryExecutor.remaskStoredCredentials() {
    remaskReports()
    remaskGroups()
}

private suspend fun QueryExecutor.remaskReports() {
    var after = 0L
    while (true) {
        val page =
            fetchAll(
                Statement
                    .create(
                        "SELECT id, message, stacktrace, context, breadcrumbs FROM reports WHERE id > :after ORDER BY id LIMIT $PAGE",
                    ).apply { bind("after", after) },
                StoredReportRowMapper,
            ).getOrThrow()
        if (page.isEmpty()) return

        page.forEach { row ->
            val message = CredentialMask.mask(row.message)
            val stacktrace = CredentialMask.mask(row.stacktrace)
            val context = row.context?.let(::remaskContext)
            val breadcrumbs = row.breadcrumbs?.let(::remaskBreadcrumbs)
            val changed =
                message != row.message ||
                    stacktrace != row.stacktrace ||
                    context != row.context ||
                    breadcrumbs != row.breadcrumbs
            if (changed) {
                execute(
                    Statement
                        .create(
                            "UPDATE reports SET message = :message, stacktrace = :stacktrace, context = :context, breadcrumbs = :breadcrumbs WHERE id = :id",
                        ).apply {
                            bind("message", message)
                            bind("stacktrace", stacktrace)
                            bind("context", context)
                            bind("breadcrumbs", breadcrumbs)
                            bind("id", row.id)
                        },
                ).getOrThrow()
            }
        }
        after = page.last().id
    }
}

private suspend fun QueryExecutor.remaskGroups() {
    var after = 0L
    while (true) {
        val page =
            fetchAll(
                Statement
                    .create(
                        "SELECT id, app_id, title, message FROM error_groups WHERE id > :after ORDER BY id LIMIT $PAGE",
                    ).apply { bind("after", after) },
                StoredGroupRowMapper,
            ).getOrThrow()
        if (page.isEmpty()) return

        page.forEach { row ->
            val title = CredentialMask.mask(row.title)
            val message = row.message?.let(CredentialMask::mask)
            if (title != row.title || message != row.message) {
                execute(
                    Statement
                        .create("UPDATE error_groups SET title = :title, message = :message WHERE id = :id")
                        .apply {
                            bind("title", title)
                            bind("message", message)
                            bind("id", row.id)
                        },
                ).getOrThrow()
            }
            if (title != row.title) refingerprint(row)
        }
        after = page.last().id
    }
}

private suspend fun QueryExecutor.refingerprint(group: StoredGroupRow) {
    val latest =
        fetchAll(
            Statement
                .create("SELECT stacktrace FROM reports WHERE group_id = :groupId ORDER BY id DESC LIMIT 1")
                .apply { bind("groupId", group.id) },
            StacktraceMapper,
        ).getOrThrow()
            .firstOrNull() ?: return

    val fingerprint = ProcessReportUseCase.generateFingerprint(latest)
    val holder =
        fetchAll(
            Statement
                .create("SELECT id FROM error_groups WHERE app_id = :appId AND fingerprint = :fingerprint")
                .apply {
                    bind("appId", group.appId)
                    bind("fingerprint", fingerprint)
                },
            IdMapper,
        ).getOrThrow()
            .firstOrNull()
    if (holder != null) return

    execute(
        Statement
            .create("UPDATE error_groups SET fingerprint = :fingerprint WHERE id = :id")
            .apply {
                bind("fingerprint", fingerprint)
                bind("id", group.id)
            },
    ).getOrThrow()
}

/**
 * The context as ingest now stores it: each value masked, re-encoded the way the repository encodes.
 * A context the mask leaves alone is returned as stored, byte for byte, so it is not rewritten for a
 * difference in formatting; text that does not parse is masked as text — still a client's words.
 */
private fun remaskContext(raw: String): String {
    val context =
        runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrNull() ?: return CredentialMask.mask(raw)
    val masked = context.mapValues { (_, value) -> CredentialMask.mask(value) }
    return if (masked == context) raw else Json.encodeToString(masked)
}

private fun remaskBreadcrumbs(raw: String): String {
    val breadcrumbs =
        runCatching { Json.decodeFromString<List<Breadcrumb>>(raw) }.getOrNull() ?: return CredentialMask.mask(raw)
    val masked = breadcrumbs.map(Breadcrumb::maskCredentials)
    return if (masked == breadcrumbs) raw else Json.encodeToString(masked)
}
