package ru.workinprogress.katcher.ui

import kotlinx.html.HTMLTag
import kotlinx.html.unsafe

object Icons {
    fun HTMLTag.copy() =
        unsafe {
            +
                """
                <svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path d="M4 2h11v2H6v13H4V2zm4 4h12v16H8V6zm2 2v12h8V8h-8z" fill="currentColor"/>
                </svg>
                """.trimIndent()
        }

    fun HTMLTag.check() =
        unsafe {
            +
                """
                <svg fill="none" viewBox="0 0 24 24">
                    <path d="M18 6h2v2h-2V6zm-2 4V8h2v2h-2zm-2 2v-2h2v2h-2zm-2 2h2v-2h-2v2zm-2 2h2v-2h-2v2zm-2 0v2h2v-2H8zm-2-2h2v2H6v-2zm0 0H4v-2h2v2z"
                        fill="currentColor"/>
                </svg>
                """.trimIndent()
        }

    fun HTMLTag.cloud() =
        unsafe {
            +
                """
                <svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"> <path d="M16 4h-6v2H8v2H4v2H2v2H0v6h2v2h20v-2h2v-6h-2v-2h-2V8h-2V6h-2V4zm2 8h4v6H2v-6h2v-2h4v2h2v-2H8V8h2V6h6v2h2v4zm0 0v2h-2v-2h2z" fill="currentColor"/> </svg>
                """.trimIndent()
        }
}
