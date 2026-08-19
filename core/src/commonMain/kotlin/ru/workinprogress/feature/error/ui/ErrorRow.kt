package ru.workinprogress.feature.error.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.ErrorGroupWithViewed
import ru.workinprogress.feature.report.GroupActivity
import ru.workinprogress.katcher.ui.Icons.check
import ru.workinprogress.katcher.ui.Spark.sparkLine
import ru.workinprogress.katcher.utils.ageWords
import ru.workinprogress.katcher.utils.epochMillis

/**
 * The states one row can be in, most urgent first. Nothing here is colour-only: the edge
 * carries the state at a glance and the badge names it in words, because "the yellow one"
 * is not something a person can act on or repeat to somebody else.
 */
private enum class RowState {
    Resolved,
    Regressed,
    FixProposed,
    New,
    Viewed,
    ;

    val edgeClasses: String
        get() =
            when (this) {
                New -> "border-l-primary"
                Regressed -> "border-l-secondary"
                FixProposed -> "border-l-accent"
                Resolved, Viewed -> "border-l-transparent"
            }

    val textClasses: String
        get() =
            when (this) {
                New, Regressed, FixProposed -> ""
                Viewed -> "text-foreground/62"
                Resolved -> "text-foreground/50"
            }

    /** The colour the trend line is drawn in — the row's own state, not a second opinion. */
    val lineColor: String
        get() =
            when (this) {
                New -> "var(--color-primary)"
                Regressed -> "var(--color-secondary)"
                FixProposed -> "var(--color-accent)"
                Viewed, Resolved -> "currentColor"
            }
}

private fun stateOf(item: ErrorGroupWithViewed): RowState {
    val group = item.errorGroup

    return when {
        group.resolved -> RowState.Resolved
        group.regressed -> RowState.Regressed
        group.fixUrl != null -> RowState.FixProposed
        !item.viewed -> RowState.New
        else -> RowState.Viewed
    }
}

context(call: ApplicationCall)
fun FlowContent.errorRow(
    appId: Int,
    item: ErrorGroupWithViewed,
    activity: GroupActivity?,
    now: Long,
) {
    val group = item.errorGroup
    val state = stateOf(item)

    div(
        classes =
            "flex items-center gap-3 px-4 py-3 border-b border-border border-l-[3px] " +
                "${state.edgeClasses} ${state.textClasses} cursor-pointer " +
                "hover:bg-accent hover:text-accent-foreground transition",
    ) {
        id = "group-row-${group.id}"

        attributes.hx {
            get =
                call.application.href(
                    AppsResource.AppId.Errors.GroupId(
                        parent = AppsResource.AppId.Errors(appId = appId),
                        groupId = group.id,
                    ),
                )
            pushUrl = "true"
            target = "body"
            swap = HxSwap.outerHtml
        }

        div(classes = "flex-1 min-w-0 flex flex-col gap-1.5") {
            div(classes = "flex items-baseline gap-2 min-w-0") {
                if (state == RowState.Resolved) {
                    span(classes = "w-3.5 h-3.5 flex-none") { check() }
                }

                // The type never truncates and the message always does: two rows of the same
                // exception differ in the message, and two different exceptions differ before
                // the message starts.
                span(classes = "font-mono text-sm font-semibold flex-none") {
                    +(
                        group.exceptionType ?: group.title
                            .lineSequence()
                            .first()
                            .take(80)
                    )
                }

                group.message?.let { message ->
                    span(classes = "text-sm truncate") { +message }
                }
            }

            div(classes = "flex items-center gap-2.5 text-xs text-muted-foreground") {
                span(classes = "font-mono flex-none") {
                    // Saying nothing here would read as "no location known" rather than "the
                    // crash never passed through code of ours".
                    +(group.location ?: "no app frame · driver only")
                }

                activity?.let { seen ->
                    val origin = listOfNotNull(seen.environment, seen.releases).joinToString(" · ")
                    if (origin.isNotEmpty()) {
                        rowDivider()
                        span(classes = "font-mono truncate") { +origin }
                    }
                }

                rowBadge(state, group.regressedRelease, group.fixUrl)
            }
        }

        div(classes = "flex-none") {
            sparkLine(
                values = activity?.dailyCrashes ?: List(TREND_POINTS) { 0 },
                color = state.lineColor,
                label = "crashes per day",
            )
        }

        div(classes = "w-16 flex-none text-right text-base font-semibold tabular-nums") {
            +group.occurrences.toString()
        }

        div(classes = "w-26 flex-none text-right text-[13px] font-mono") {
            +ageWords((now - group.lastSeen.epochMillis()).coerceAtLeast(0))
        }
    }
}

private fun FlowContent.rowDivider() {
    span(classes = "w-px h-3 bg-border flex-none")
}

private fun FlowContent.rowBadge(
    state: RowState,
    regressedRelease: String?,
    fixUrl: String?,
) {
    val badgeBase = "flex-none text-[10px] font-semibold tracking-[0.08em] uppercase px-1.5 py-0.5"

    when (state) {
        RowState.New -> {
            span(classes = "$badgeBase bg-primary text-primary-foreground") { +"New" }
        }

        RowState.Regressed -> {
            span(classes = "$badgeBase bg-secondary text-secondary-foreground") {
                +(regressedRelease?.let { "Regressed in $it" } ?: "Regressed")
            }
        }

        RowState.FixProposed -> {
            span(classes = "$badgeBase bg-accent text-accent-foreground") {
                +("Fix proposed" + pullRequestNumber(fixUrl)?.let { " · PR $it" }.orEmpty())
            }
        }

        RowState.Resolved -> {
            span(classes = "$badgeBase border border-border") { +"Resolved" }
        }

        // A row somebody has already opened and nobody has acted on carries no badge: the
        // absence is the state.
        RowState.Viewed -> {
            Unit
        }
    }
}

/** Days of trend a row shows — the same window the app card counts over. */
private const val TREND_POINTS = 7

/** The tail of a pull request URL, when it is a number. Anything else is not shortened. */
private fun pullRequestNumber(fixUrl: String?): String? =
    fixUrl
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
