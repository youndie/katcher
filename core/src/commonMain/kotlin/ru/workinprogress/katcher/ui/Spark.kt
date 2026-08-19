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
                +"""<svg width="$WIDTH" height="$HEIGHT" viewBox="0 0 $WIDTH $HEIGHT" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="$label"><line x1="0" y1="${HEIGHT - 0.5}" x2="$WIDTH" y2="${HEIGHT - 0.5}" stroke="currentColor" stroke-opacity="0.25" stroke-width="1"/></svg>"""
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
            +"""<svg width="$WIDTH" height="$HEIGHT" viewBox="0 0 $WIDTH $HEIGHT" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="$label">$bars</svg>"""
        }
    }
}
