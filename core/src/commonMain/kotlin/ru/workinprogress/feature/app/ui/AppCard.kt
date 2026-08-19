package ru.workinprogress.feature.app.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.BUTTON
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppKey
import ru.workinprogress.feature.app.AppOverview
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.app.label
import ru.workinprogress.katcher.ui.Icons.copy
import ru.workinprogress.katcher.ui.Spark.sparkBars
import ru.workinprogress.katcher.utils.ageWords
import ru.workinprogress.katcher.utils.maskKey
import ru.workinprogress.katcher.utils.silenceWords

/**
 * State of one card, in the order the card is read: what is on fire, then what is waiting,
 * then silence. Silence is a state too — it is stated in words rather than coloured,
 * because an app that reports nothing is not necessarily an app that is fine.
 */
private enum class CardState {
    Burning,
    FixWaiting,
    Crashing,
    Quiet,
    ;

    val edgeClasses: String
        get() =
            when (this) {
                Burning, Crashing -> "border-l-primary"
                FixWaiting -> "border-l-accent"
                Quiet -> "border-l-border"
            }
}

private fun stateOf(overview: AppOverview): CardState =
    when {
        overview.newGroupsToday > 0 -> CardState.Burning
        overview.fixesWaiting > 0 -> CardState.FixWaiting
        overview.crashes24h > 0 -> CardState.Crashing
        else -> CardState.Quiet
    }

context(call: ApplicationCall)
fun FlowContent.appCard(
    app: App,
    overview: AppOverview,
    keys: List<AppKey>,
    now: Long,
    revealKey: Boolean = false,
) {
    val state = stateOf(overview)

    div(
        classes =
            "border border-border bg-card text-card-foreground flex flex-col " +
                "transition hover:border-foreground/40 cursor-pointer pointer-events-auto",
    ) {
        id = "app-card-${app.id}"

        attributes.hx {
            get = call.application.href(AppsResource.AppId(appId = app.id))
            trigger = "click"
            pushUrl = "true"
            target = "body"
            swap = HxSwap.outerHtml
        }

        div(classes = "p-4 flex items-start justify-between gap-3 border-l-[3px] ${state.edgeClasses}") {
            div(classes = "flex flex-col gap-1.5 min-w-0") {
                div(classes = "flex items-center gap-2") {
                    span(
                        classes =
                            "text-[17px] font-semibold truncate " +
                                if (state == CardState.Quiet) "text-foreground/70" else "",
                    ) { +app.name }

                    span(
                        classes =
                            "text-[10px] font-semibold tracking-[0.08em] uppercase px-1.5 py-0.5 " +
                                "border border-border text-muted-foreground",
                    ) { +app.type.label }
                }

                div(classes = "text-[13px] font-mono text-muted-foreground") {
                    +silenceWords(overview.lastCrashAt, now)
                }
            }

            div(classes = "flex items-center gap-2 flex-none") {
                if (overview.newGroupsToday > 0) {
                    cardBadge("${overview.newGroupsToday} new today", "bg-primary text-primary-foreground")
                } else if (overview.fixesWaiting > 0) {
                    cardBadge(fixWords(overview.fixesWaiting), "bg-accent text-accent-foreground")
                }

                div {
                    id = "app-menu-${app.id}"
                    appMenuButton(app.id)
                }
            }
        }

        if (overview.neverReported) {
            neverReportedBody()
        } else {
            numbersRow(overview, state)
        }

        appKeyRow(app.id, keys, now, revealKey)
    }
}

private fun FlowContent.cardBadge(
    text: String,
    colorClasses: String,
) {
    span(
        classes =
            "flex-none text-[10px] font-semibold tracking-[0.08em] uppercase px-1.5 py-1 $colorClasses",
    ) { +text }
}

private fun FlowContent.numbersRow(
    overview: AppOverview,
    state: CardState,
) {
    div(
        classes =
            "grid-cols-3 border-t border-border " +
                // Three zeroes and a flat line are not worth a third of a phone screen.
                if (state == CardState.Quiet) "hidden sm:grid text-foreground/70" else "grid",
    ) {
        numberCell(overview.unseenGroups.toString(), "unseen groups", withDivider = true)
        numberCell(overview.crashes24h.toString(), "crashes / 24h", withDivider = true)

        div(classes = "p-3 px-4 flex flex-col gap-1.5") {
            div(classes = "text-muted-foreground") {
                sparkBars(overview.dailyCrashes, "crashes per day, last ${AppOverview.DAYS} days")
            }
            cellLabel("${AppOverview.DAYS} days")
        }
    }
}

private fun FlowContent.numberCell(
    value: String,
    label: String,
    withDivider: Boolean,
) {
    div(classes = "p-3 px-4 flex flex-col gap-0.5 " + if (withDivider) "border-r border-border" else "") {
        div(classes = "text-[26px] leading-none font-semibold tabular-nums") { +value }
        cellLabel(label)
    }
}

private fun FlowContent.cellLabel(text: String) {
    div(classes = "text-[11px] tracking-[0.06em] uppercase text-muted-foreground") { +text }
}

private fun FlowContent.neverReportedBody() {
    div(classes = "p-5 px-4 border-t border-border flex flex-col gap-2.5") {
        div(classes = "text-[13px] leading-relaxed text-muted-foreground max-w-[380px]") {
            +"No report has ever arrived with this key. Add "
            code(classes = "font-mono text-foreground") { +"Katcher.start { }" }
            +" to the app, or check that the key it ships is this one."
        }
    }
}

/**
 * The key line of a card. Public because it is also a fragment on its own: Reveal, a reissue
 * and a revoke each swap this one element.
 */
context(call: ApplicationCall)
fun FlowContent.appKeyRow(
    appId: Int,
    keys: List<AppKey>,
    now: Long,
    revealKey: Boolean,
) {
    val active = keys.filter { key -> key.active }
    val current = active.firstOrNull()
    val superseded = active.drop(1)

    div(classes = "px-4 py-2.5 border-t border-border flex flex-col gap-2") {
        id = "app-key-$appId"

        if (current == null) {
            div(classes = "flex items-center justify-between gap-3") {
                span(classes = "text-xs font-mono text-muted-foreground") { +"no key — nothing can report" }
                issueButton(appId, label = "Issue key")
            }
            return@div
        }

        div(classes = "flex items-center justify-between gap-3") {
            if (revealKey) {
                span(classes = "text-xs font-mono text-foreground truncate") { +current.key }

                button(classes = "ml-2 text-muted-foreground hover:text-foreground transition cursor-pointer") {
                    attributes["onclick"] =
                        """
                        event.stopPropagation();
                        navigator.clipboard.writeText('${current.key}');
                        """.trimIndent()

                    div("w-4 h-4") { copy() }
                }
            } else {
                span(classes = "text-xs font-mono text-muted-foreground truncate") {
                    +"key ${maskKey(current.key)}"
                }

                keyRowButton(label = "Reveal") {
                    attributes.hx {
                        get = call.application.href(AppsResource.AppId.Key(appId = appId))
                        target = "#app-key-$appId"
                        swap = HxSwap.outerHtml
                    }
                }
            }
        }

        // After a reissue the old key is still accepted, and shipped builds are still using
        // it. Saying when it was last used is what turns "revoke" from a gamble into a call.
        superseded.forEach { old ->
            div(classes = "flex items-center justify-between gap-3 text-xs font-mono text-muted-foreground") {
                span(classes = "truncate") {
                    +"previous ${maskKey(old.key)} · ${keyUseWords(old, now)}"
                }

                keyRowButton(label = "Revoke") {
                    attributes.hx {
                        post =
                            call.application.href(
                                AppsResource.AppId.Keys.Revoke(appId = appId, keyId = old.id),
                            )
                        target = "#app-key-$appId"
                        swap = HxSwap.outerHtml
                    }
                }
            }
        }
    }
}

context(call: ApplicationCall)
private fun FlowContent.issueButton(
    appId: Int,
    label: String,
) {
    keyRowButton(label) {
        attributes.hx {
            post = call.application.href(AppsResource.AppId.Keys(appId = appId))
            target = "#app-key-$appId"
            swap = HxSwap.outerHtml
        }
    }
}

private fun FlowContent.keyRowButton(
    label: String,
    block: BUTTON.() -> Unit,
) {
    button(
        classes =
            "h-[26px] px-2.5 text-xs border border-border text-muted-foreground " +
                "hover:text-foreground transition cursor-pointer flex-none",
    ) {
        // The card itself is a link; a button inside it is not a way to open the app.
        attributes["onclick"] = "event.stopPropagation();"
        block()
        +label
    }
}

/** When a key last carried a report — or that it never has. */
private fun keyUseWords(
    key: AppKey,
    now: Long,
): String = key.lastUsedAt?.let { used -> "last used ${ageWords((now - used).coerceAtLeast(0))}" } ?: "never used"

private fun fixWords(count: Int): String = if (count == 1) "1 fix waiting" else "$count fixes waiting"
