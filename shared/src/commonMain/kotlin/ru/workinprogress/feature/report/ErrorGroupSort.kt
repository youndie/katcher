package ru.workinprogress.feature.report

import kotlinx.serialization.Serializable

@Serializable
@Suppress("ktlint:standard:enum-entry-name-case")
enum class ErrorGroupSort {
    id,
    title,
    lastSeen,
    occurrences,
}

@Serializable
@Suppress("ktlint:standard:enum-entry-name-case")
enum class ErrorGroupSortOrder {
    asc,
    desc,
}
