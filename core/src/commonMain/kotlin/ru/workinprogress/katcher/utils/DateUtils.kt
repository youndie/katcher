@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.katcher.utils

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
 * Back to epoch milliseconds. Timestamps are stored as milliseconds and only turned into a
 * local date on the way out, so anything that needs to measure age has to turn them back.
 */
fun LocalDateTime.epochMillis(): Long = toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
