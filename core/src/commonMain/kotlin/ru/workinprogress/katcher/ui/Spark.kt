@file:Suppress("ktlint:standard:max-line-length")

package ru.workinprogress.katcher.ui

import kotlinx.html.HTMLTag
import kotlinx.html.unsafe

/**
 * The only charts in this interface: bars for a per-day count, a polyline for a trend.
 *
 * Both are drawn here, on the server, into static SVG — the page has no chart library and
 * no client state to feed one, and a fragment swapped by htmx has to arrive already drawn.
 */
object Spark {
    private const val WIDTH = 112
    private const val HEIGHT = 26
    private const val GAP = 4

    private const val LINE_WIDTH = 104
    private const val LINE_HEIGHT = 22

    /**
     * The trend of one group over the same window, as a line. Colour is passed in rather than
     * derived: the row already decided what state it is in, and two places deciding that
     * separately is how they drift apart.
     */
    fun HTMLTag.sparkLine(
        values: List<Int>,
        color: String,
        label: String,
    ) {
        if (values.size < 2) return

        val max = values.max()
        val step = LINE_WIDTH.toDouble() / (values.size - 1)
        val points =
            values
                .mapIndexed { index, value ->
                    val x = (index * step).toInt()
                    // Nothing in the window draws a baseline rather than nothing at all: an
                    // empty cell reads as "no chart", a flat line reads as "no crashes".
                    val y = if (max == 0) LINE_HEIGHT - 1 else LINE_HEIGHT - (value.toDouble() / max * (LINE_HEIGHT - 2)).toInt() - 1
                    "$x,$y"
                }.joinToString(" ")

        unsafe {
            +"""<svg width="$LINE_WIDTH" height="$LINE_HEIGHT" viewBox="0 0 $LINE_WIDTH $LINE_HEIGHT" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="$label" style="flex:none"><polyline points="$points" stroke="$color" stroke-width="1.5" fill="none"/></svg>"""
        }
    }

    /**
     * Bar heights in pixels, relative to the tallest value in the series. A non-zero count
     * never rounds down to nothing: a day with one crash has to be distinguishable from a
     * day with none.
     */
    fun barHeights(
        values: List<Int>,
        height: Int = HEIGHT,
    ): List<Int> {
        val max = values.maxOrNull() ?: return emptyList()
        if (max == 0) return values.map { 0 }

        return values.map { value ->
            if (value == 0) 1 else (value.toDouble() / max * height).toInt().coerceAtLeast(2)
        }
    }

    /**
     * Daily counts, oldest first. The last bar is the running day and is drawn in the
     * primary colour; heights are relative to the tallest bar in the series, so the shape
     * is a comparison inside one app and never between two.
     */
    fun HTMLTag.sparkBars(
        values: List<Int>,
        label: String,
    ) {
        if (values.isEmpty()) return

        val max = values.max()
        if (max == 0) {
            // A flat line rather than a row of zero-height bars: nothing arrived, and that
            // should look like nothing, not like a chart that failed to draw.
            unsafe {
                +"""<svg width="$WIDTH" height="$HEIGHT" viewBox="0 0 $WIDTH $HEIGHT" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="$label" style="max-width:100%;height:auto"><line x1="0" y1="${HEIGHT - 0.5}" x2="$WIDTH" y2="${HEIGHT - 0.5}" stroke="currentColor" stroke-opacity="0.25" stroke-width="1"/></svg>"""
            }
            return
        }

        val barWidth = (WIDTH - GAP * (values.size - 1)) / values.size
        val heights = barHeights(values)
        val bars =
            values
                .mapIndexed { index, value ->
                    val height = heights[index]
                    val x = index * (barWidth + GAP)
                    val y = HEIGHT - height
                    val fill =
                        if (index == values.lastIndex && value > 0) {
                            """fill="var(--color-primary)""""
                        } else {
                            """fill="currentColor" fill-opacity="0.35""""
                        }
                    """<rect x="$x" y="$y" width="$barWidth" height="$height" $fill/>"""
                }.joinToString("")

        unsafe {
            +"""<svg width="$WIDTH" height="$HEIGHT" viewBox="0 0 $WIDTH $HEIGHT" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="$label" style="max-width:100%;height:auto">$bars</svg>"""
        }
    }
}
