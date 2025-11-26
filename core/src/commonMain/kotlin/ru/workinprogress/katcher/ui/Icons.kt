@file:Suppress("ktlint:standard:max-line-length")

package ru.workinprogress.katcher.ui

import kotlinx.html.HTMLTag
import kotlinx.html.unsafe

object Icons {
    fun HTMLTag.logo() =
        unsafe {
            +"""<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
    <defs>
        <linearGradient id="katcher_grad" x1="10" y1="10" x2="54" y2="54" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stop-color="#7F52FF"/> <stop offset="100%" stop-color="#00C2FF"/> </linearGradient>
        <filter id="soft_shadow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur in="SourceAlpha" stdDeviation="1" result="blur"/>
            <feOffset in="blur" dx="0" dy="1" result="offsetBlur"/>
            <feFlood flood-color="#000000" flood-opacity="0.2" result="offsetColor"/>
            <feComposite in="offsetColor" in2="offsetBlur" operator="in" result="offsetBlur"/>
            <feMerge>
                <feMergeNode in="offsetBlur"/>
                <feMergeNode in="SourceGraphic"/>
            </feMerge>
        </filter>
    </defs>
    
    <g filter="url(#soft_shadow)">
        <path d="M14 12C14 9.79086 15.7909 8 18 8H30L42 26L32 26L14 12Z" fill="url(#katcher_grad)"/>
        
        <path d="M14 24L30 36H44L52 48C53.1046 49.6569 51.9176 52 49.9282 52H18C15.7909 52 14 50.2091 14 48V24Z" fill="url(#katcher_grad)"/>
    </g>
</svg>"""
        }

    fun HTMLTag.logoMonochrome() =
        unsafe {
            +"""<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M14 12C14 9.79086 15.7909 8 18 8H30L42 26L32 26L14 12Z" fill="currentColor"/>
    
    <path d="M14 24L30 36H44L52 48C53.1046 49.6569 51.9176 52 49.9282 52H18C15.7909 52 14 50.2091 14 48V24Z" fill="currentColor"/>
</svg>"""
        }

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
            +"""<svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"> <path d="M16 4h-6v2H8v2H4v2H2v2H0v6h2v2h20v-2h2v-6h-2v-2h-2V8h-2V6h-2V4zm2 8h4v6H2v-6h2v-2h4v2h2v-2H8V8h2V6h6v2h2v4zm0 0v2h-2v-2h2z" fill="currentColor"/> </svg>"""
                .trimIndent()
        }

    fun HTMLTag.bug() =
        unsafe {
            +"""<svg xmlns="http://www.w3.org/2000/svg" fill="currentColor" viewBox="0 0 24 24"> <path d="M6 2h2v2H6V2Zm4 9h4v2h-4v-2Zm4 4h-4v2h4v-2Z"/> <path d="M16 4h-2v2h-4V4H8v2H6v3H4V7H2v2h2v2h2v2H2v2h4v2H4v2H2v2h2v-2h2v3h12v-3h2v2h2v-2h-2v-2h-2v-2h4v-2h-4v-2h2V9h2V7h-2v2h-2V6h-2V4ZM8 20V8h8v12H8Zm8-16V2h2v2h-2Z"/> </svg>"""
                .trimIndent()
        }
}
