package ru.workinprogress.feature.report.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.code
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h4
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.ErrorGroup
import ru.workinprogress.feature.error.ui.stackTracePanel
import ru.workinprogress.feature.report.GroupActivity
import ru.workinprogress.feature.report.ReleaseCount
import ru.workinprogress.feature.report.Report
import ru.workinprogress.feature.report.ReportsPaginated
import ru.workinprogress.katcher.ui.ButtonSize
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.Icons.check
import ru.workinprogress.katcher.ui.Icons.close
import ru.workinprogress.katcher.ui.Icons.info
import ru.workinprogress.katcher.ui.Spark.sparkBars
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.ui.fragmentSlot
import ru.workinprogress.katcher.ui.infoRow
import ru.workinprogress.katcher.ui.toastSlot
import ru.workinprogress.katcher.ui.uiButton
import ru.workinprogress.katcher.ui.uiCard
import ru.workinprogress.katcher.ui.uiCardContent
import ru.workinprogress.katcher.ui.uiCardDescription
import ru.workinprogress.katcher.ui.uiCardHeader
import ru.workinprogress.katcher.ui.uiCardTitle
import ru.workinprogress.katcher.utils.ageWords
import ru.workinprogress.katcher.utils.epochMillis
import ru.workinprogress.katcher.utils.human

context(call: ApplicationCall)
fun HTML.errorGroupPage(
    appId: Int,
    group: ErrorGroup,
    stackTrace: String,
    activity: GroupActivity?,
    releases: List<ReleaseCount>,
    now: Long,
) {
    head {
        title("Error — ${group.exceptionType ?: group.title}")
        commonHead()
    }

    body(classes = "bg-background text-foreground min-h-screen") {
        div(classes = "mx-auto max-w-5xl p-6 space-y-5") {
            div(classes = "flex items-center gap-2.5 text-xs font-mono text-muted-foreground") {
                span(classes = "cursor-pointer hover:text-foreground transition") {
                    attributes.hx {
                        get = call.application.href(AppsResource())
                        pushUrl = "true"
                        target = "body"
                        swap = HxSwap.outerHtml
                    }
                    +"apps"
                }
                +"/"
                span(classes = "cursor-pointer hover:text-foreground transition") {
                    attributes.hx {
                        get = call.application.href(AppsResource.AppId(appId = appId))
                        pushUrl = "true"
                        target = "body"
                        swap = HxSwap.outerHtml
                    }
                    +"errors"
                }
                +"/"
                span(classes = "text-foreground") { +"group #${group.id}" }
            }

            div(classes = "flex items-start justify-between gap-6 flex-wrap") {
                div(classes = "flex flex-col gap-2 min-w-0") {
                    div(classes = "flex items-center gap-2.5") {
                        span(classes = "w-[3px] h-5.5 flex-none ${edgeColor(group)}")
                        h1(classes = "font-mono text-2xl font-semibold tracking-tight break-words") {
                            +(group.exceptionType ?: group.title.lineSequence().first())
                        }
                    }

                    group.message?.let { message ->
                        p(classes = "text-base leading-relaxed text-foreground/88 max-w-2xl") { +message }
                    }

                    div(classes = "flex items-center gap-2.5 text-[13px] font-mono text-muted-foreground") {
                        span(classes = "text-foreground") {
                            +(group.location ?: "no app frame · driver only")
                        }
                        activity?.let { seen ->
                            listOfNotNull(seen.environment, seen.releases).forEach { fact ->
                                +"·"
                                span { +fact }
                            }
                        }
                    }
                }

                div(classes = "flex flex-col items-end gap-2 flex-none") {
                    div(classes = "flex gap-2") {
                        uiButton(variant = ButtonVariant.Outline, size = ButtonSize.Sm) {
                            attributes["onclick"] =
                                "window.location='" +
                                call.application.href(
                                    AppsResource.AppId.Errors.GroupId.CrashJson(
                                        parent =
                                            AppsResource.AppId.Errors.GroupId(
                                                appId = appId,
                                                groupId = group.id,
                                            ),
                                    ),
                                ) + "'"
                            +"crash.json"
                        }

                        div {
                            id = "group-status"
                            groupStatusFragment(appId, group)
                        }
                    }

                    span(classes = "text-[11px] font-mono text-muted-foreground") {
                        +"fingerprint ${group.fingerprint.take(10)}"
                    }
                }
            }

            group.fixUrl?.let { fixUrl ->
                div(
                    classes =
                        "border border-accent bg-card flex items-center justify-between gap-4 " +
                            "px-4 py-3 flex-wrap",
                ) {
                    div(classes = "flex items-center gap-3 min-w-0") {
                        span(
                            classes =
                                "flex-none text-[10px] font-semibold tracking-[0.08em] uppercase " +
                                    "px-1.5 py-0.5 bg-accent text-accent-foreground",
                        ) { +"Fix proposed" }
                        span(classes = "text-sm") {
                            +"An agent read crash.json and opened a pull request. Waiting for a human."
                        }
                    }

                    // The full URL is the link text on purpose. This value was written by an
                    // agent, and Katcher holds no repository configuration to check the host
                    // against — so the only defence left is letting a person see where the
                    // link actually goes before clicking it.
                    a(href = fixUrl, classes = "text-[13px] font-mono text-accent break-all text-right") {
                        rel = "noopener noreferrer nofollow"
                        target = "_blank"
                        +fixUrl
                    }
                }
            }

            groupFacts(group, activity, releases, now)

            stackTracePanel(appId, group.id, stackTrace, expandAll = false)

            fragmentSlot(
                slotId = "reports-table",
                url =
                    call.application.href(
                        AppsResource.AppId.Errors.GroupId.Reports.Paginated(
                            groupId = group.id,
                            appId = appId,
                        ),
                    ),
                header = {
                    div(classes = "flex items-center gap-2.5") {
                        span(classes = "text-[15px] font-semibold") { +"Reports" }
                        span(classes = "text-xs font-mono text-muted-foreground") {
                            +"${group.occurrences} total, newest first"
                        }
                    }
                },
                placeholder = {
                    div(classes = "animate-pulse space-y-3 p-4") {
                        div(classes = "h-3 bg-muted w-1/3")
                        div(classes = "h-3 bg-muted w-full")
                        div(classes = "h-3 bg-muted w-2/3")
                    }
                },
            )

            toastSlot()
        }
    }
}

/** Resolve, or — once somebody has resolved it — Reopen. Swapped on its own. */
context(call: ApplicationCall)
fun FlowContent.groupStatusFragment(
    appId: Int,
    group: ErrorGroup,
) {
    val groupId = AppsResource.AppId.Errors.GroupId(appId = appId, groupId = group.id)

    if (group.resolved) {
        uiButton(variant = ButtonVariant.Outline, size = ButtonSize.Sm) {
            attributes.hx {
                post =
                    call.application.href(
                        AppsResource.AppId.Errors.GroupId
                            .Reopen(parent = groupId),
                    )
                target = "#group-status"
                swap = HxSwap.innerHtml
            }
            +"Reopen"
        }
    } else {
        uiButton(variant = ButtonVariant.Default, size = ButtonSize.Sm) {
            attributes.hx {
                post =
                    call.application.href(
                        AppsResource.AppId.Errors.GroupId
                            .Resolve(parent = groupId),
                    )
                target = "#group-status"
                swap = HxSwap.innerHtml
            }
            +"Resolve"
        }
    }
}

private fun edgeColor(group: ErrorGroup): String =
    when {
        group.resolved -> "bg-border"
        group.regressed -> "bg-secondary"
        group.fixUrl != null -> "bg-accent"
        else -> "bg-primary"
    }

/**
 * Occurrences, the shape of the window, which releases carry it, and when it was seen. The
 * order is the order the questions get asked: how bad, since when, whose build, how fresh.
 */
context(call: ApplicationCall)
private fun FlowContent.groupFacts(
    group: ErrorGroup,
    activity: GroupActivity?,
    releases: List<ReleaseCount>,
    now: Long,
) {
    div(classes = "border border-border bg-card text-card-foreground grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4") {
        factCell("Occurrences", divider = true) {
            div(classes = "text-[28px] leading-none font-semibold tabular-nums") {
                +group.occurrences.toString()
            }
            activity?.let { seen ->
                div(classes = "text-xs font-mono text-muted-foreground") {
                    +"${seen.dailyCrashes.lastOrNull() ?: 0} in the last 24 h"
                }
            }
        }

        factCell("Last ${activity?.dailyCrashes?.size ?: 0} days", divider = true) {
            div(classes = "text-muted-foreground") {
                sparkBars(activity?.dailyCrashes.orEmpty(), "crashes per day")
            }
            div(classes = "text-xs font-mono text-muted-foreground") {
                +trendWords(activity?.dailyCrashes.orEmpty())
            }
        }

        factCell("Releases", divider = true) {
            if (releases.isEmpty()) {
                div(classes = "text-xs font-mono text-muted-foreground") { +"no release reported" }
            } else {
                val top = releases.maxOf { it.count }
                div(classes = "flex flex-col gap-1.5") {
                    releases.forEach { release ->
                        div(classes = "flex items-center gap-2 text-xs font-mono") {
                            span(classes = "w-12 flex-none truncate") { +release.release }
                            span(classes = "flex-1 h-2 bg-foreground/12") {
                                span(
                                    classes = "block h-2 bg-primary",
                                ) {
                                    attributes["style"] = "width:${release.count * 100 / top}%"
                                }
                            }
                            span(classes = "w-8 text-right text-muted-foreground") { +release.count.toString() }
                        }
                    }
                }
            }
        }

        factCell("Seen", divider = false) {
            div(classes = "flex flex-col gap-1.5 text-[13px] font-mono") {
                seenRow("first", group.firstSeen.human())
                seenRow("last", ageWords((now - group.lastSeen.epochMillis()).coerceAtLeast(0)))
                seenRow("env", activity?.environment ?: "mixed")
            }
        }
    }
}

private fun FlowContent.factCell(
    label: String,
    divider: Boolean,
    block: FlowContent.() -> Unit,
) {
    div(
        classes =
            "p-4 flex flex-col gap-2 " +
                if (divider) "border-b border-border lg:border-b-0 lg:border-r" else "",
    ) {
        div(classes = "text-[11px] tracking-[0.08em] uppercase text-muted-foreground") { +label }
        block()
    }
}

private fun FlowContent.seenRow(
    label: String,
    value: String,
) {
    div(classes = "flex justify-between gap-2") {
        span(classes = "text-muted-foreground") { +label }
        span { +value }
    }
}

/**
 * Said only when the window actually shows it: the last third of the days against the first
 * third. Anything closer than a fifth apart is called steady rather than dressed up.
 */
private fun trendWords(daily: List<Int>): String {
    if (daily.size < 3 || daily.sum() == 0) return "nothing in this window"

    val slice = daily.size / 3
    val early = daily.take(slice).sum()
    val late = daily.takeLast(slice).sum()

    return when {
        early == 0 && late > 0 -> "rising"
        early == 0 -> "steady"
        late * 5 > early * 6 -> "rising"
        late * 6 < early * 5 -> "falling"
        else -> "steady"
    }
}

context(call: ApplicationCall)
fun HTML.reportsTableFragment(
    appId: Int,
    groupId: Long,
    data: ReportsPaginated,
) {
    body {
        data.items.forEach { report ->
            val reportUrl =
                call.application.href(
                    AppsResource.AppId.Errors.GroupId.Reports.ReportId(
                        appId = appId,
                        groupId = groupId,
                        reportId = report.id,
                    ),
                )

            div(
                classes =
                    "flex items-center gap-4 px-4 py-2.5 border-b border-border text-[13px] " +
                        "cursor-pointer hover:bg-accent hover:text-accent-foreground transition",
            ) {
                attributes.hx {
                    get = reportUrl
                    pushUrl = "true"
                    target = "body"
                    swap = HxSwap.outerHtml
                }

                span(classes = "w-32 flex-none font-mono") { +report.timestamp.human() }
                span(classes = "w-16 flex-none font-mono truncate") { +(report.release ?: "—") }
                span(classes = "flex-1 min-w-0 truncate") { +report.message }
                span(classes = "flex-none text-xs font-mono text-muted-foreground") {
                    +reportContents(report)
                }
                span(classes = "flex-none text-xs font-mono") { +"Open →" }
            }
        }

        div(classes = "flex items-center justify-between gap-2 px-4 py-3") {
            span(classes = "text-xs font-mono text-muted-foreground") {
                +"page ${data.page} of ${data.totalPages}"
            }

            div(classes = "flex gap-2") {
                if (data.page > 1) {
                    uiButton(
                        variant = ButtonVariant.Outline,
                        size = ButtonSize.Sm,
                    ) {
                        attributes.hx {
                            get =
                                call.application.href(
                                    AppsResource.AppId.Errors.GroupId.Reports.Paginated(
                                        groupId = groupId,
                                        appId = appId,
                                        page = data.page - 1,
                                    ),
                                )
                            target = "#reports-table-body"
                            swap = HxSwap.innerHtml
                        }
                        +"← Prev"
                    }
                }

                if (data.page < data.totalPages) {
                    uiButton(
                        variant = ButtonVariant.Outline,
                        size = ButtonSize.Sm,
                    ) {
                        attributes.hx {
                            get =
                                call.application.href(
                                    AppsResource.AppId.Errors.GroupId.Reports.Paginated(
                                        groupId = groupId,
                                        appId = appId,
                                        page = data.page + 1,
                                    ),
                                )
                            target = "#reports-table-body"
                            swap = HxSwap.innerHtml
                        }
                        +"Next →"
                    }
                }
            }
        }
    }
}

/**
 * What is in a report besides its message. Counted rather than listed: the row has no space
 * for six context keys, and "6 keys" is enough to decide whether to open it.
 */
private fun reportContents(report: Report): String {
    val keys = report.context?.size ?: 0
    val crumbs = report.breadcrumbs?.size ?: 0

    return listOfNotNull(
        keys.takeIf { it > 0 }?.let { "context $it keys" },
        crumbs.takeIf { it > 0 }?.let { "$it breadcrumbs" },
    ).joinToString(" · ")
}
