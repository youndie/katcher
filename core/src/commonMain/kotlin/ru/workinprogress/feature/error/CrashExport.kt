package ru.workinprogress.feature.error

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import ru.workinprogress.feature.report.Report

/**
 * A crash handed to an external AI fixer. The consumer downloads this file and commits it
 * into their own repository, where a workflow of their choosing picks it up — Katcher
 * itself never talks to GitHub and holds no tokens.
 *
 * The field names are the wire contract with that workflow: renaming one silently breaks
 * every consumer's prompt-building step, so treat this as a published schema.
 */
@Serializable
data class CrashExport(
    val crashId: String,
    val fingerprint: String,
    val release: String,
    val stacktrace: String,
    val occurrences: Int,
    val firstSeen: String,
    val lastSeen: String,
    val context: Map<String, String>,
)

sealed interface CrashExportResult {
    data class Ok(
        val export: CrashExport,
        val fileName: String,
    ) : CrashExportResult

    data class Rejected(
        val reason: String,
    ) : CrashExportResult
}

/** Stand-in when a report carries no release; the fixer requires a non-empty value. */
private const val UNKNOWN_RELEASE = "unknown"

/**
 * Caps on the `context` map. It is free-form data supplied by the reporting app, and the
 * export ends up committed to a git repository — where it stays in history permanently,
 * even after the fix PR deletes the file. Bounding it keeps one misbehaving app from
 * dumping an unbounded blob into someone's repo and prompt.
 */
private const val MAX_CONTEXT_ENTRIES = 32
private const val MAX_CONTEXT_VALUE_LENGTH = 512

private const val MAX_FILE_NAME_FINGERPRINT_LENGTH = 60

/**
 * Builds the export, or rejects it with a reason to show the user.
 *
 * Rejecting is deliberate: an export that looks fine but carries no stacktrace produces a
 * confidently useless PR, which is the outcome this whole pipeline is meant to avoid.
 */
fun buildCrashExport(
    group: ErrorGroup,
    latestReport: Report?,
): CrashExportResult {
    if (latestReport == null) {
        return CrashExportResult.Rejected(
            "This group has no reports stored, so there is no stacktrace to send.",
        )
    }

    // The group page falls back to `group.title` when a stacktrace is missing, which is
    // fine for display but would hand the fixer a one-line "stacktrace" it cannot act on.
    if (latestReport.stacktrace.isBlank()) {
        return CrashExportResult.Rejected(
            "The latest report for this group has an empty stacktrace.",
        )
    }

    val export =
        CrashExport(
            crashId = group.id.toString(),
            fingerprint = group.fingerprint,
            release = latestReport.release?.takeIf { it.isNotBlank() } ?: UNKNOWN_RELEASE,
            stacktrace = latestReport.stacktrace,
            occurrences = group.occurrences,
            firstSeen = group.firstSeen.toIsoUtc(),
            lastSeen = group.lastSeen.toIsoUtc(),
            context = latestReport.context.orEmpty().capped(),
        )

    return CrashExportResult.Ok(export, crashFileName(group))
}

/**
 * Rows are stored as epoch millis and read back through [TimeZone.currentSystemDefault],
 * so these [LocalDateTime]s are server-local, not UTC. Converting through the same zone
 * yields a real instant; simply appending "Z" would mislabel local time as UTC.
 */
private fun LocalDateTime.toIsoUtc(): String = toInstant(TimeZone.currentSystemDefault()).toString()

private fun Map<String, String>.capped(): Map<String, String> =
    entries
        .sortedBy { it.key }
        .take(MAX_CONTEXT_ENTRIES)
        .associate { (key, value) -> key to value.take(MAX_CONTEXT_VALUE_LENGTH) }

/**
 * The fingerprint is app-supplied, and this value lands in a `Content-Disposition` header
 * and then on the user's filesystem. Restricting it to a conservative character set keeps
 * it from injecting header parameters or escaping the intended directory.
 */
private fun crashFileName(group: ErrorGroup): String {
    val safeFingerprint =
        group.fingerprint
            .map { if (it.isLetterOrDigit() || it == '.' || it == '_' || it == '-') it else '-' }
            .joinToString("")
            .trim('-')
            .take(MAX_FILE_NAME_FINGERPRINT_LENGTH)
            .ifBlank { group.id.toString() }

    return "crash-$safeFingerprint.json"
}
