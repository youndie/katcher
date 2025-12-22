package ru.workinprogress.feature.report.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.DIV
import kotlinx.html.HTML
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
import ru.workinprogress.feature.report.ReportsPaginated
import ru.workinprogress.katcher.ui.ButtonSize
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.Icons.check
import ru.workinprogress.katcher.ui.Icons.close
import ru.workinprogress.katcher.ui.Icons.info
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.ui.infoRow
import ru.workinprogress.katcher.ui.uiButton
import ru.workinprogress.katcher.ui.uiCard
import ru.workinprogress.katcher.ui.uiCardContent
import ru.workinprogress.katcher.ui.uiCardDescription
import ru.workinprogress.katcher.ui.uiCardHeader
import ru.workinprogress.katcher.ui.uiCardTitle
import ru.workinprogress.katcher.utils.human

fun DIV.resolvedFragment() {
    div(classes = "flex justify-between items-center space-x-2") {
        div(classes = "h-8 w-8") {
            check()
        }
        p(classes = "text-muted-foreground font-mono") { +"Resolved" }
    }
}

context(call: ApplicationCall)
fun HTML.errorGroupPage(
    appId: Int,
    group: ErrorGroup,
    stackTrace: String,
) {
    head {
        title("Error — ${group.title}")
        commonHead()
    }

    body(classes = "bg-background text-foreground min-h-screen") {
        div(classes = "mx-auto max-w-5xl p-6 space-y-6") {
            div(classes = "flex justify-between mb-8") {
                uiButton(
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Sm,
                ) {
                    attributes.hx {
                        get =
                            call.application.href(
                                AppsResource.AppId(appId = appId),
                            )
                        pushUrl = "true"
                        target = "body"
                        swap = HxSwap.outerHtml
                    }
                    +"← Back"
                }

                div {
                    id = "resolved-container"

                    if (!group.resolved) {
                        uiButton(variant = ButtonVariant.Default) {
                            attributes.hx {
                                post =
                                    call.application.href(
                                        AppsResource.AppId.Errors.GroupId.Resolve(
                                            parent =
                                                AppsResource.AppId.Errors.GroupId(
                                                    appId = appId,
                                                    groupId = group.id,
                                                ),
                                        ),
                                    )
                                target = "#resolved-container"
                                swap = HxSwap.outerHtml
                            }
                            +"Resolve"
                        }
                    } else {
                        resolvedFragment()
                    }
                }
            }

            h1(classes = "text-2xl font-bold tracking-tight break-words") { +group.title }
            p(classes = "text-muted-foreground text-sm") { +"Group #${group.id}" }

            uiCard {
                uiCardHeader {
                    uiCardTitle { +"Summary" }
                    uiCardDescription { +"Overview of group activity" }
                }
                uiCardContent {
                    div(classes = "grid grid-cols-2 gap-4 text-sm") {
                        infoRow("Occurrences", group.occurrences.toString())
                        infoRow("First seen", group.firstSeen.human())
                        infoRow("Last seen", group.lastSeen.human())
                        infoRow("Fingerprint", group.fingerprint.take(18))
                    }
                }
            }

            uiCard {
                uiCardHeader {
                    uiCardTitle { +"Stacktrace" }
                    uiCardDescription { +"Error source details" }
                }
                uiCardContent {
                    pre(
                        classes =
                            "bg-muted text-muted-foreground p-4 rounded-lg text-sm text-sm whitespace-pre-wrap break-words",
                    ) {
                        code { +stackTrace }
                    }
                }
            }

            uiCard {
                uiCardHeader {
                    uiCardTitle { +"Reports" }
                }
                uiCardContent {
                    div {
                        id = "reports-table"
                        attributes.hx {
                            get =
                                call.application.href(
                                    AppsResource.AppId.Errors.GroupId.Reports.Paginated(
                                        groupId = group.id,
                                        appId = appId,
                                    ),
                                )
                            trigger = "load"
                            swap = HxSwap.innerHtml
                        }

                        // Little skeleton
                        div(classes = "animate-pulse space-y-3") {
                            div(classes = "h-3 bg-muted rounded w-1/3")
                            div(classes = "h-3 bg-muted rounded w-full")
                            div(classes = "h-3 bg-muted rounded w-2/3")
                        }
                    }
                }
            }
        }
    }
}

context(call: ApplicationCall)
fun HTML.reportsTableFragment(
    appId: Int,
    groupId: Long,
    data: ReportsPaginated,
) {
    body {
        table(
            classes =
                "w-full border table-fixed border-border rounded-xl text-sm " +
                    "bg-card text-card-foreground",
        ) {
            thead(
                classes = "bg-muted text-muted-foreground border-b border-border",
            ) {
                tr {
                    th(classes = "p-2 text-left w-40 whitespace-nowrap") { +"Timestamp" }
                    th(classes = "p-2 text-left") { +"Message" }
                    th(classes = "p-2 text-left") { +"Environment" }
                }
            }

            tbody {
                data.items.forEach { report ->
                    tr(
                        classes =
                            "border-b border-border hover:bg-accent " +
                                "hover:text-accent-foreground transition",
                    ) {
                        td(classes = "p-2 w-40 whitespace-nowrap") {
                            +report.timestamp.human()
                        }

                        td(classes = "p-2 max-w-xs") {
                            val context = report.context

                            if (context.isNullOrEmpty()) {
                                div(classes = "truncate") { +report.message }
                            } else {
                                details(classes = "group relative") {
                                    summary(
                                        classes = "list-none cursor-pointer flex items-center gap-2 truncate",
                                    ) {
                                        span(classes = "h-4 w-4 shrink-0") { info() }
                                        span(classes = "truncate") { +report.message }
                                    }

                                    div(
                                        classes =
                                            """absolute left-0 bottom-full w-96 p-4 rounded-md border 
                                               bg-card text-card-foreground shadow-xl z-50""",
                                    ) {
                                        div(classes = "flex justify-between items-center mb-2") {
                                            h4(classes = "text-sm font-semibold") { +"Context Data" }
                                            span(classes = "h-4 w-4 cursor-pointer hover:bg-muted rounded") {
                                                attributes["onclick"] =
                                                    "event.preventDefault(); this.closest('details').removeAttribute('open')"
                                                close()
                                            }
                                        }

                                        table(classes = "w-full text-xs") {
                                            tbody {
                                                context.forEach { (k, v) ->
                                                    tr(classes = "border-b border-border/50 last:border-0") {
                                                        td(classes = "py-1 font-medium text-muted-foreground w-1/3 pr-4") { +k }
                                                        td(classes = "py-1 font-mono text-foreground break-all") { +v }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        td(classes = "p-2") {
                            text(report.environment.orEmpty())
                        }
                    }
                }
            }
        }

        div(classes = "flex gap-2 mt-4") {
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
                        target = "#reports-table"
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
                        target = "#reports-table"
                        swap = HxSwap.innerHtml
                    }
                    +"Next →"
                }
            }
        }
    }
}
