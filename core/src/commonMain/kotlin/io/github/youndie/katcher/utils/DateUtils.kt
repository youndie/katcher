@file:OptIn(ExperimentalTime::class)

package io.github.youndie.katcher.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

fun LocalDateTime.human(): String {
    val yyyy = year.toString().padStart(4, '0')
    val monthNumber = month.number.toString().padStart(2, '0')
    val dd = day.toString().padStart(2, '0')
    val hh = hour.toString().padStart(2, '0')
    val mm = minute.toString().padStart(2, '0')

    return "$yyyy-$monthNumber-$dd $hh:$mm"
}

/**
 * How long ago, in the words the interface uses: "4 min ago", "3 h ago", "6 days ago".
 * Nothing here is coloured or emphasised — age is stated, and the reader decides.
 */
fun ageWords(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

/** Plural-safe "for 6 days" tail used by the silence line on an app card. */
fun daysWords(millis: Long): String {
    val days = millis / (24 * 60 * 60 * 1000L)
    return if (days == 1L) "1 day" else "$days days"
}

/**
 * All but the last four characters replaced. The tail is kept so a key on screen can be
 * matched against the one an application ships without revealing the key itself.
 */
fun maskKey(key: String): String {
    if (key.length <= 4) return "•".repeat(key.length)
    return "•".repeat(key.length - 4) + key.takeLast(4)
}

/**
 * Two different silences, said in words rather than shown in colour. An app that reported
 * before and went quiet is a fact; one that never reported at all is a suspicion about the
 * wiring, and only that one is worth explaining on the card.
 */
fun silenceWords(
    lastCrashAt: Long?,
    now: Long,
): String {
    if (lastCrashAt == null) return "never reported"

    val age = (now - lastCrashAt).coerceAtLeast(0)
    return if (age < 24 * 60 * 60 * 1000L) "last crash ${ageWords(age)}" else "quiet for ${daysWords(age)}"
}

/**
 * The zone rows are read back in, looked up once for the life of the process.
 *
 * On Kotlin/Native `TimeZone.currentSystemDefault()` is not cached by the platform: the lookup is
 * redone on every call and costs 33 µs against 73 ns for reading the clock (issue #78). One row
 * of the error list went through it three times — twice mapping the row out of the database and
 * once rendering it — so a page paid for the lookup as many times as it had rows.
 *
 * Caching costs the ability to notice the operator changing the process's zone while it runs.
 * Daylight saving is not affected: this is the zone, not an offset, and it resolves the offset per
 * instant on its own.
 *
 * Only server-side code depends on `:core`, so nothing here freezes a zone inside an application
 * carrying the SDK — a device that travels is a different question, and it is issue #78's.
 */
val serverZone: TimeZone = TimeZone.currentSystemDefault()

/**
 * Back to epoch milliseconds. Timestamps are stored as milliseconds and only turned into a
 * local date on the way out, so anything that needs to measure age has to turn them back.
 */
fun LocalDateTime.epochMillis(): Long = toInstant(serverZone).toEpochMilliseconds()
