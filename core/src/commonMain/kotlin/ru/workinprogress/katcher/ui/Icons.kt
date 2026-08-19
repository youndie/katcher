@file:Suppress("ktlint:standard:max-line-length")

package ru.workinprogress.katcher.ui

import kotlinx.html.HTMLTag
import kotlinx.html.unsafe

object Icons {
    /**
     * The mark: a lit bomb on a 16×16 grid, drawn as whole pixels.
     *
     * Sized in multiples of 16 wherever it appears — at 40 or 56 the grid lands on half
     * pixels and the edges blur, which is the one thing pixel art cannot survive. The body
     * follows the text colour so the same file works in both themes; only the lit fuse is
     * coloured, and it is the primary red the interface already uses for "this is burning".
     */
    fun HTMLTag.logo(size: Int = 48) =
        unsafe {
            +"""<svg width="$size" height="$size" viewBox="0 0 16 16" shape-rendering="crispEdges" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Katcher">
    <rect x="5" y="6" width="6" height="1" fill="currentColor"/>
    <rect x="4" y="7" width="8" height="1" fill="currentColor"/>
    <rect x="3" y="8" width="10" height="3" fill="currentColor"/>
    <rect x="4" y="11" width="8" height="1" fill="currentColor"/>
    <rect x="5" y="12" width="6" height="1" fill="currentColor"/>
    <rect x="5" y="8" width="1" height="1" fill="var(--color-background)"/>
    <rect x="9" y="4" width="1" height="2" fill="currentColor"/>
    <rect x="10" y="3" width="1" height="1" fill="var(--color-primary)"/>
    <rect x="11" y="2" width="2" height="1" fill="var(--color-primary)"/>
    <rect x="12" y="1" width="1" height="1" fill="var(--color-primary)"/>
</svg>"""
        }

    /**
     * One colour, spark included — for anywhere the tokens do not reach: a favicon, an embed,
     * a foreign page.
     */
    fun HTMLTag.logoMonochrome(size: Int = 48) =
        unsafe {
            +"""<svg width="$size" height="$size" viewBox="0 0 16 16" shape-rendering="crispEdges" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Katcher">
    <rect x="5" y="6" width="6" height="1" fill="currentColor"/>
    <rect x="4" y="7" width="8" height="1" fill="currentColor"/>
    <rect x="3" y="8" width="10" height="3" fill="currentColor"/>
    <rect x="4" y="11" width="8" height="1" fill="currentColor"/>
    <rect x="5" y="12" width="6" height="1" fill="currentColor"/>
    <rect x="9" y="4" width="1" height="2" fill="currentColor"/>
    <rect x="10" y="3" width="1" height="1" fill="currentColor"/>
    <rect x="11" y="2" width="2" height="1" fill="currentColor"/>
    <rect x="12" y="1" width="1" height="1" fill="currentColor"/>
</svg>"""
        }

    fun HTMLTag.copy() =
        unsafe {
            val copySvg =
                """
                <svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path d="M4 2h11v2H6v13H4V2zm4 4h12v16H8V6zm2 2v12h8V8h-8z" fill="currentColor"/>
                </svg>
                """.trimIndent()
            +copySvg
        }

    /** The one moving thing in this interface: a fragment says it is still loading. */
    fun HTMLTag.spinner() =
        unsafe {
            +"""<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" class="animate-spin"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="3" stroke-dasharray="14 40"/></svg>"""
        }

    fun HTMLTag.check() =
        unsafe {
            val checkSvg =
                """
                <svg fill="none" viewBox="0 0 24 24">
                    <path d="M18 6h2v2h-2V6zm-2 4V8h2v2h-2zm-2 2v-2h2v2h-2zm-2 2h2v-2h-2v2zm-2 2h2v-2h-2v2zm-2 0v2h2v-2H8zm-2-2h2v2H6v-2zm0 0H4v-2h2v2z"
                        fill="currentColor"/>
                </svg>
                """.trimIndent()
            +checkSvg
        }

    fun HTMLTag.cloud() =
        unsafe {
            +"""<svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"> <path d="M16 4h-6v2H8v2H4v2H2v2H0v6h2v2h20v-2h2v-6h-2v-2h-2V8h-2V6h-2V4zm2 8h4v6H2v-6h2v-2h4v2h2v-2H8V8h2V6h6v2h2v4zm0 0v2h-2v-2h2z" fill="currentColor"/> </svg>"""
                .trimIndent()
        }

    fun HTMLTag.bug() =
        unsafe {
            +"""<svg xmlns="http://www.w3.org/2000/svg" fill="currentColor" viewBox="0 0 24 24"> <path d="M6 2h2v2H6V2Zm4 9h4v2h-4v-2Zm4 4h-4v2h4v-2Z"/> <path d="M16 4h-2v2h-4V4H8v2H6v3H4V7H2v2h2v2h2v2H2v2h4v2H4v2H2v2h2v-2h2v3h12v-3h2v2h2v-2h-2v-2h-2v-2h4v-2h-4v-2h2V9h2V7h-2v2h-2V6h-2V4ZM8 20V8h8v12H8Zm8-16V2h2v2h-2Z"/> </svg>"""
                .trimIndent()
        }

    fun HTMLTag.info() =
        unsafe {
            +"""<svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"> <path d="M3 3h2v18H3V3zm16 0H5v2h14v14H5v2h16V3h-2zm-8 6h2V7h-2v2zm2 8h-2v-6h2v6z" fill="currentColor"/> </svg>"""
        }

    fun HTMLTag.close() =
        unsafe {
            +"""<svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"> <path d="M5 5h2v2H5V5zm4 4H7V7h2v2zm2 2H9V9h2v2zm2 0h-2v2H9v2H7v2H5v2h2v-2h2v-2h2v-2h2v2h2v2h2v2h2v-2h-2v-2h-2v-2h-2v-2zm2-2v2h-2V9h2zm2-2v2h-2V7h2zm0 0V5h2v2h-2z" fill="currentColor"/> </svg>"""
        }
}
