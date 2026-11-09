package io.github.youndie.katcher.db

import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.RowMapper
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.youndie.katcher.feature.error.ProcessReportUseCase

private class GroupKey(
    val id: Long,
    val appId: Int,
    val fingerprint: String,
    val firstSeen: Long,
)

private object GroupKeyMapper : RowMapper<GroupKey> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ) = GroupKey(
        id = row.get("id").asLong(),
        appId = row.get("app_id").asInt(),
        fingerprint = row.get("fingerprint").asString(),
        firstSeen = row.get("first_seen").asLong(),
    )
}

private object LatestStacktraceMapper : RowMapper<String> {
    override fun map(
        row: ResultSet.Row,
        converters: ValueEncoderRegistry,
    ) = row.get("stacktrace").asString()
}

/**
 * Brings every stored group to the fingerprint ingest computes today, merging the groups that then
 * share one.
 *
 * A group is found by its fingerprint and nothing else, so a change to
 * [ProcessReportUseCase.generateFingerprint] alone would make the next report of every open group a
 * new group — the defect such a change exists to remove, applied to the whole store at once. Any such
 * change therefore ships with a migration step that calls this (B-02 is the first).
 *
 * Each group's fingerprint is recomputed from its latest report, the text ingest stored after
 * symbolication — what the next report of the same failure will be hashed from. Groups are visited
 * oldest first, and where two of one app now share a fingerprint the newer is merged into the older:
 * its reports move, occurrences add up, the first and last sightings widen, it stays resolved only if
 * both were, a linked fix and a regression are kept from whichever has one, and the "viewed" marks of
 * both are dropped so the merged group reads as news. A group without reports is left as it is.
 *
 * A second run finds every fingerprint already current and no two groups sharing one, and writes
 * nothing.
 */
internal suspend fun QueryExecutor.refingerprintGroups() {
    val groups =
        fetchAll(
            Statement.create("SELECT id, app_id, fingerprint, first_seen FROM error_groups ORDER BY first_seen, id"),
            GroupKeyMapper,
        ).getOrThrow()

    val merged = mutableSetOf<Long>()
    for (group in groups) {
        if (group.id in merged) continue
        val latest = latestStacktrace(group.id) ?: continue
        val fingerprint = ProcessReportUseCase.generateFingerprint(latest)
        if (fingerprint == group.fingerprint) continue

        val holder = holderOf(group.appId, fingerprint, except = group.id)
        if (holder == null) {
            setFingerprint(group.id, fingerprint)
            continue
        }

        // The older of the two survives, whichever of them already carried the fingerprint.
        val (target, other) =
            if (holder.firstSeen < group.firstSeen || (holder.firstSeen == group.firstSeen && holder.id < group.id)) {
                holder to group
            } else {
                group to holder
            }
        merge(into = target.id, from = other.id)
        merged += other.id
        setFingerprint(target.id, fingerprint)
    }
}

private suspend fun QueryExecutor.latestStacktrace(groupId: Long): String? =
    fetchAll(
        Statement
            .create("SELECT stacktrace FROM reports WHERE group_id = :groupId ORDER BY id DESC LIMIT 1")
            .apply { bind("groupId", groupId) },
        LatestStacktraceMapper,
    ).getOrThrow()
        .firstOrNull()

private suspend fun QueryExecutor.holderOf(
    appId: Int,
    fingerprint: String,
    except: Long,
): GroupKey? =
    fetchAll(
        Statement
            .create(
                "SELECT id, app_id, fingerprint, first_seen FROM error_groups WHERE app_id = :appId AND fingerprint = :fingerprint AND id != :except",
            ).apply {
                bind("appId", appId)
                bind("fingerprint", fingerprint)
                bind("except", except)
            },
        GroupKeyMapper,
    ).getOrThrow()
        .firstOrNull()

private suspend fun QueryExecutor.setFingerprint(
    groupId: Long,
    fingerprint: String,
) {
    execute(
        Statement
            .create("UPDATE error_groups SET fingerprint = :fingerprint WHERE id = :id")
            .apply {
                bind("fingerprint", fingerprint)
                bind("id", groupId)
            },
    ).getOrThrow()
}

private suspend fun QueryExecutor.merge(
    into: Long,
    from: Long,
) {
    val statements =
        listOf(
            """
            UPDATE error_groups SET
                occurrences = occurrences + (SELECT occurrences FROM error_groups WHERE id = :from),
                first_seen = MIN(first_seen, (SELECT first_seen FROM error_groups WHERE id = :from)),
                last_seen = MAX(last_seen, (SELECT last_seen FROM error_groups WHERE id = :from)),
                resolved = (resolved AND (SELECT resolved FROM error_groups WHERE id = :from)),
                fix_url = COALESCE(fix_url, (SELECT fix_url FROM error_groups WHERE id = :from)),
                fix_linked_at = COALESCE(fix_linked_at, (SELECT fix_linked_at FROM error_groups WHERE id = :from)),
                regressed_at = COALESCE(regressed_at, (SELECT regressed_at FROM error_groups WHERE id = :from)),
                regressed_release = COALESCE(regressed_release, (SELECT regressed_release FROM error_groups WHERE id = :from))
            WHERE id = :into
            """,
            "UPDATE reports SET group_id = :into WHERE group_id = :from",
            "DELETE FROM user_error_group_viewed WHERE group_id IN (:into, :from)",
            "DELETE FROM error_groups WHERE id = :from",
        )
    statements.forEach { sql ->
        execute(
            Statement.create(sql).apply {
                if (":into" in sql) bind("into", into)
                bind("from", from)
            },
        ).getOrThrow()
    }
}
