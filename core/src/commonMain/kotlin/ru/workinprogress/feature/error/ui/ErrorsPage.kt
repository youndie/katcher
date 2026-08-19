package ru.workinprogress.feature.error.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.span
import kotlinx.html.title
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppOverview
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.app.label
import ru.workinprogress.feature.error.ErrorGroupsPaginated
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder
import ru.workinprogress.feature.report.GroupActivity
import ru.workinprogress.katcher.ui.ButtonSize
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.Icons.bug
import ru.workinprogress.katcher.ui.Spark.sparkBars
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.ui.uiButton

context(call: ApplicationCall)
fun HTML.appErrorsPage(
    app: App,
    overview: AppOverview,
) {
    head {
        title("Errors — ${app.name}")
        commonHead()
    }

    body(classes = "bg-background text-foreground min-h-screen") {
        div(classes = "mx-auto max-w-5xl p-6 space-y-6") {
            div(classes = "flex items-center justify-between gap-4 flex-wrap") {
                div(classes = "flex items-center gap-3.5 min-w-0") {
                    uiButton(
                        variant = ButtonVariant.Outline,
                        size = ButtonSize.Sm,
                    ) {
                        attributes.hx {
                            get = call.application.href(AppsResource())
                            pushUrl = "true"
                            target = "body"
                            swap = HxSwap.outerHtml
                        }
                        +"← Apps"
                    }

                    h1(classes = "text-2xl font-semibold tracking-tight truncate") { +app.name }

                    span(
                        classes =
                            "text-[10px] font-semibold tracking-[0.08em] uppercase px-1.5 py-0.5 " +
                                "border border-border text-muted-foreground flex-none",
                    ) { +app.type.label }
                }

                div(classes = "flex items-center gap-2.5") {
                    div(classes = "flex items-baseline gap-1.5") {
                        span(classes = "text-xl font-semibold tabular-nums") { +overview.crashes24h.toString() }
                        span(classes = "text-xs uppercase tracking-[0.06em] text-muted-foreground") {
                            +"crashes / 24h"
                        }
                    }

                    div(classes = "text-muted-foreground") {
                        sparkBars(overview.dailyCrashes, "crashes per day, last ${AppOverview.DAYS} days")
                    }
                }
            }

            div(
                classes = "bg-card text-card-foreground border border-border",
            ) {
                id = "errors-table"

                attributes.hx {
                    get =
                        call.application.href(
                            AppsResource.AppId.Errors(
                                appId = app.id,
                            ),
                        )
                    trigger = "load"
                    swap = HxSwap.innerHtml
                }
            }
        }
    }
}

context(call: ApplicationCall)
fun HTML.errorsTableFragment(
    appId: Int,
    data: ErrorGroupsPaginated,
    activity: Map<Long, GroupActivity>,
    now: Long,
) {
    body {
        if (data.items.isEmpty()) {
            div(classes = "flex flex-col items-center justify-center py-16 text-center space-y-4") {
                id = "empty-view"

                div(
                    classes = "w-16 h-16 p-4 rounded-full bg-muted flex items-center justify-center",
                ) {
                    bug()
                }

                h2(classes = "text-xl font-semibold") {
                    +"Nothing has arrived yet"
                }

                p(classes = "text-muted-foreground max-w-sm") {
                    +(
                        "Either nothing is crashing, or reports are not reaching this key. " +
                            "Send one on purpose to check the pipe."
                    )
                }

                pre(
                    classes = "font-mono text-sm bg-muted p-4 rounded-lg text-left whitespace-pre-wrap leading-relaxed max-w-sm",
                ) {
                    val snippet =
                        """
                        try {
                            riskyCode()
                        } catch (t: Throwable) {
                            Katcher.catch(t)
                        }
                        """.trimIndent()
                    +snippet
                }
            }
        }

        if (data.items.isNotEmpty()) {
            div(
                classes =
                    "flex items-center gap-3 px-4 py-2 border-b border-border " +
                        "text-[11px] tracking-[0.08em] uppercase text-muted-foreground",
            ) {
                sortCell(appId, ErrorGroupSort.title, "Error", data, "flex-1 min-w-0")
                span(classes = "w-26 flex-none") { +"Trend" }
                sortCell(appId, ErrorGroupSort.occurrences, "Count", data, "w-16 flex-none text-right")
                sortCell(appId, ErrorGroupSort.lastSeen, "Last seen", data, "w-26 flex-none text-right")
            }

            data.items.forEach { item ->
                errorRow(appId, item, activity[item.errorGroup.id], now)
            }
        }

        div(classes = "flex gap-2 mt-4") {
            if (data.page > 1) {
                uiButton(variant = ButtonVariant.Outline) {
                    attributes.hx {
                        get =
                            call.application.href(
                                AppsResource.AppId.Errors.Paginated(
                                    parent = AppsResource.AppId.Errors(appId = appId),
                                    sortBy = data.sortBy,
                                    sortOrder = data.sortOrder,
                                    page = data.page - 1,
                                ),
                            )
                        target = "#errors-table"
                        swap = HxSwap.innerHtml
                    }
                    +"← Prev"
                }
            }

            if (data.page < data.totalPages) {
                uiButton(variant = ButtonVariant.Outline) {
                    attributes.hx {
                        get =
                            call.application.href(
                                AppsResource.AppId.Errors.Paginated(
                                    parent = AppsResource.AppId.Errors(appId = appId),
                                    sortBy = data.sortBy,
                                    sortOrder = data.sortOrder,
                                    page = data.page + 1,
                                ),
                            )
                        target = "#errors-table"
                        swap = HxSwap.innerHtml
                    }
                    +"Next →"
                }
            }
        }
    }
}

/** A column header that is also the sort link — the sort lives in the URL, not on the client. */
context(call: ApplicationCall)
fun FlowContent.sortCell(
    appId: Int,
    field: ErrorGroupSort,
    label: String,
    data: ErrorGroupsPaginated,
    extraClasses: String,
) {
    span(
        classes = "cursor-pointer hover:text-foreground transition $extraClasses",
    ) {
        attributes.hx {
            get =
                call.application.href(
                    AppsResource.AppId.Errors.Paginated(
                        parent = AppsResource.AppId.Errors(appId = appId),
                        sortBy = field,
                        sortOrder =
                            if (data.sortBy == field && data.sortOrder == ErrorGroupSortOrder.asc) {
                                ErrorGroupSortOrder.desc
                            } else {
                                ErrorGroupSortOrder.asc
                            },
                    ),
                )
            target = "#errors-table"
            swap = HxSwap.innerHtml
        }

        if (data.sortBy == field) {
            span(classes = "text-foreground") {
                +(label + if (data.sortOrder == ErrorGroupSortOrder.desc) " ↓" else " ↑")
            }
        } else {
            +label
        }
    }
}
