package ru.workinprogress.feature.report.ui

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.span
import ru.workinprogress.feature.report.Report
import ru.workinprogress.katcher.utils.human

/**
 * The context a report carried, as a table of keys and values.
 *
 * Everything here was written by the reporting application and is read by an engineer who
 * will paste half of it somewhere else, so all of it is monospace and none of it is
 * truncated — a context value cut at the edge is the one that mattered.
 */
fun FlowContent.reportContextBlock(report: Report) {
    val context = report.context.orEmpty()

    div(classes = "border border-border") {
        blockHeader("Context", if (context.isEmpty()) "none" else "${context.size} keys")

        if (context.isEmpty()) {
            div(classes = "px-4 py-3 text-xs font-mono text-muted-foreground") {
                +"the report carried no context"
            }
        } else {
            div(classes = "divide-y divide-border") {
                context.entries.sortedBy { entry -> entry.key }.forEach { (key, value) ->
                    div(classes = "flex items-start gap-4 px-4 py-2 text-xs font-mono") {
                        span(classes = "w-40 flex-none text-muted-foreground break-all") { +key }
                        span(classes = "flex-1 min-w-0 break-all") { +value }
                    }
                }
            }
        }
    }
}

/** What happened before the crash, oldest first, with the crash itself closing the list. */
fun FlowContent.reportBreadcrumbsBlock(report: Report) {
    val breadcrumbs = report.breadcrumbs.orEmpty()
    if (breadcrumbs.isEmpty()) return

    div(classes = "border border-border") {
        blockHeader("Breadcrumbs", "${breadcrumbs.size} before the crash")

        div(classes = "divide-y divide-border") {
            breadcrumbs.forEach { breadcrumb ->
                div(classes = "flex items-start gap-3 px-4 py-2 text-xs") {
                    span(classes = "w-32 flex-none font-mono text-muted-foreground") {
                        +breadcrumb.timestamp.human()
                    }
                    span(
                        classes =
                            "w-20 flex-none text-[10px] font-semibold tracking-[0.08em] uppercase " +
                                "text-muted-foreground",
                    ) { +breadcrumb.type }
                    div(classes = "flex-1 min-w-0 flex flex-col gap-1") {
                        span(classes = "break-words") { +breadcrumb.message }

                        breadcrumb.data?.takeIf { data -> data.isNotEmpty() }?.let { data ->
                            span(classes = "font-mono text-muted-foreground break-all") {
                                +data.entries.joinToString(" · ") { (key, value) -> "$key=$value" }
                            }
                        }
                    }
                }
            }

            div(classes = "flex items-start gap-3 px-4 py-2 text-xs border-l-[3px] border-l-primary") {
                span(classes = "w-32 flex-none font-mono text-muted-foreground") {
                    +report.timestamp.human()
                }
                span(
                    classes =
                        "w-20 flex-none text-[10px] font-semibold tracking-[0.08em] uppercase text-primary",
                ) { +"crash" }
                span(classes = "flex-1 min-w-0 break-words") { +report.message }
            }
        }
    }
}

private fun FlowContent.blockHeader(
    title: String,
    note: String,
) {
    div(classes = "px-4 py-2 border-b border-border flex items-center gap-2.5") {
        span(classes = "text-[13px] font-semibold") { +title }
        span(classes = "text-xs font-mono text-muted-foreground") { +note }
    }
}
