package ru.workinprogress.feature.app

import ru.workinprogress.katcher.ui.Spark
import ru.workinprogress.katcher.utils.maskKey
import ru.workinprogress.katcher.utils.silenceWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppCardWordsTest {
    private val now = 1_755_600_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `an app that never reported is not called quiet`() {
        assertEquals("never reported", silenceWords(lastCrashAt = null, now = now))
    }

    @Test
    fun `a recent crash is stated as an age`() {
        assertEquals("last crash 4 min ago", silenceWords(now - 4 * minute, now))
        assertEquals("last crash 3 h ago", silenceWords(now - 3 * hour, now))
    }

    @Test
    fun `past a day the card counts the silence instead`() {
        assertEquals("quiet for 6 days", silenceWords(now - 6 * day, now))
        assertEquals("quiet for 1 day", silenceWords(now - 25 * hour, now))
    }

    @Test
    fun `a clock that runs backwards does not produce a negative age`() {
        assertEquals("last crash just now", silenceWords(now + hour, now))
    }

    @Test
    fun `the key shows only its last four characters`() {
        val masked = maskKey("e21a7c4d6f804b1993aa5d0c8e77bd44")

        assertTrue(masked.endsWith("bd44"))
        assertEquals(masked.length, 32)
        assertEquals(0, masked.count { it.isLetterOrDigit() && it !in "bd44" })
    }

    @Test
    fun `a single crash in a busy week is still visible`() {
        val heights = Spark.barHeights(listOf(0, 400, 0, 0, 0, 0, 1))

        assertEquals(1, heights.first(), "a day with nothing gets a baseline, not a bar")
        assertEquals(26, heights[1], "the tallest day fills the box")
        assertTrue(heights.last() >= 2, "one crash must not round away to the baseline")
    }

    @Test
    fun `a week with no crashes has no bars at all`() {
        assertEquals(List(7) { 0 }, Spark.barHeights(List(7) { 0 }))
    }
}
