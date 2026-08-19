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
import ru.workinprogress.feature.error.StackChunk
import ru.workinprogress.feature.error.StackTrace

/**
 * The stacktrace, with runs of library frames folded into one line each.
 *
 * The toggle is a link that swaps this panel and nothing else: which frames are shown lives
 * in the query string, so a reload and a shared URL both give the same view.
 */
context(call: ApplicationCall)
fun FlowContent.stackTracePanel(
    appId: Int,
    groupId: Long,
    stacktrace: String,
    expandAll: Boolean,
) {
    val (total, own) = StackTrace.frameCounts(stacktrace)

    div(classes = "border border-border bg-card text-card-foreground") {
        id = "stacktrace"

        div(classes = "px-4 py-3 border-b border-border flex items-center justify-between gap-3") {
            div(classes = "flex items-center gap-2.5") {
                span(classes = "text-[15px] font-semibold") { +"Stacktrace" }
                span(classes = "text-xs font-mono text-muted-foreground") {
                    +"$total frames · $own yours"
                }
            }

            div(classes = "flex border border-input") {
                framesTab(appId, groupId, all = false, label = "Your frames", active = !expandAll)
                framesTab(appId, groupId, all = true, label = "All frames", active = expandAll)
            }
        }

        div(classes = "font-mono text-[13px] leading-relaxed") {
            StackTrace.fold(stacktrace, expandAll).forEach { chunk ->
                when (chunk) {
                    is StackChunk.Text -> {
                        div(classes = "px-4 py-2.5 border-b border-border last:border-b-0 whitespace-pre-wrap") {
                            +chunk.text
                        }
                    }

                    is StackChunk.Own -> {
                        div(
                            classes =
                                "px-4 py-2 border-b border-border last:border-b-0 " +
                                    "border-l-[3px] border-l-primary",
                        ) {
                            +chunk.text
                        }
                    }

                    is StackChunk.Foreign -> {
                        div(
                            classes =
                                "px-4 py-2 border-b border-border last:border-b-0 " +
                                    "bg-foreground/3 text-muted-foreground flex items-center gap-2 " +
                                    "cursor-pointer hover:text-foreground transition",
                        ) {
                            attributes.hx {
                                get = framesHref(appId, groupId, all = true)
                                target = "#stacktrace"
                                swap = HxSwap.outerHtml
                            }

                            span(classes = "text-[11px]") { +"▸" }
                            span { +chunk.label }
                        }
                    }
                }
            }
        }
    }
}

context(call: ApplicationCall)
private fun FlowContent.framesTab(
    appId: Int,
    groupId: Long,
    all: Boolean,
    label: String,
    active: Boolean,
) {
    span(
        classes =
            "h-6.5 px-2.5 inline-flex items-center text-xs cursor-pointer transition " +
                if (active) {
                    "bg-foreground text-background font-medium"
                } else {
                    "text-muted-foreground hover:text-foreground border-l border-input"
                },
    ) {
        attributes.hx {
            get = framesHref(appId, groupId, all)
            target = "#stacktrace"
            swap = HxSwap.outerHtml
        }
        +label
    }
}

context(call: ApplicationCall)
private fun framesHref(
    appId: Int,
    groupId: Long,
    all: Boolean,
): String =
    call.application.href(
        AppsResource.AppId.Errors.GroupId.Frames(
            parent = AppsResource.AppId.Errors.GroupId(appId = appId, groupId = groupId),
            all = all,
        ),
    )
