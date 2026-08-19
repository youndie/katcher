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

/**
 * What the group list was asked to show. Every field comes from the query string and nothing
 * is remembered between requests: the fragment is a pure function of the URL, so a reload and
 * a pasted link give the same list.
 */
@Serializable
data class ErrorGroupFilter(
    val query: String? = null,
    val environment: String? = null,
    val release: String? = null,
    /** Groups last seen within this many days; null is all of them. */
    val days: Int? = null,
    val unresolvedOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = query.isNullOrBlank() && environment == null && release == null && days == null && !unresolvedOnly

    /** How many of the controls are set — the number on the button that hides them on a phone. */
    val activeCount: Int
        get() =
            listOfNotNull(
                query?.takeIf { it.isNotBlank() },
                environment,
                release,
                days,
                true.takeIf { unresolvedOnly },
            ).size
}
