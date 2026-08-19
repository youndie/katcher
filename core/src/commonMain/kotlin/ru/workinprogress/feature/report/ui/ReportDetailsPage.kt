package ru.workinprogress.feature.report.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.title
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.StackChunk
import ru.workinprogress.feature.error.StackTrace
import ru.workinprogress.feature.report.Report
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.utils.human

/**
 * One report, in the same language as the group it came from: facts first, then what it
 * carried, then the trace. It is the page for the crash somebody wants to read whole — the
 * glance lives in the list, which opens a row in place.
 */
context(call: ApplicationCall)
fun HTML.reportDetailsPage(
    appId: Int,
    groupId: Long,
    report: Report,
) {
    head {
        title("Report #${report.id}")
        commonHead()
    }

    body(classes = "bg-background text-foreground min-h-screen") {
        div(classes = "mx-auto max-w-5xl p-6 flex flex-col gap-5") {
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
                span(classes = "cursor-pointer hover:text-foreground transition") {
                    attributes.hx {
                        get =
                            call.application.href(
                                AppsResource.AppId.Errors.GroupId(appId = appId, groupId = groupId),
                            )
                        pushUrl = "true"
                        target = "body"
                        swap = HxSwap.outerHtml
                    }
                    +"group #$groupId"
                }
                +"/"
                span(classes = "text-foreground") { +"report #${report.id}" }
            }

            div(classes = "flex flex-col gap-2 min-w-0") {
                h1(classes = "text-xl font-semibold leading-snug break-words") { +report.message }

                div(classes = "flex items-center gap-2.5 text-[13px] font-mono text-muted-foreground flex-wrap") {
                    span(classes = "text-foreground") { +report.timestamp.human() }
                    report.release?.let { release ->
                        +"·"
                        span { +release }
                    }
                    report.environment?.let { environment ->
                        +"·"
                        span { +environment }
                    }
                }
            }

            reportContextBlock(report)
            reportBreadcrumbsBlock(report)

            div(classes = "border border-border bg-card text-card-foreground") {
                id = "stacktrace"

                div(classes = "px-4 py-3 border-b border-border flex items-center gap-2.5") {
                    span(classes = "text-[15px] font-semibold") { +"Stacktrace" }
                    span(classes = "text-xs font-mono text-muted-foreground") {
                        val (total, own) = StackTrace.frameCounts(report.stacktrace)
                        +"$total frames · $own yours"
                    }
                }

                // Every frame, unfolded: this page is the one place somebody came to read the
                // whole thing. Long frames scroll inside the panel rather than reshaping it.
                div(classes = "font-mono text-[13px] leading-relaxed overflow-x-auto") {
                    StackTrace.fold(report.stacktrace, expandAll = true).forEach { chunk ->
                        val own = chunk is StackChunk.Own && chunk.frame.file.isNotEmpty()
                        val text =
                            when (chunk) {
                                is StackChunk.Text -> chunk.text
                                is StackChunk.Own -> chunk.text
                                is StackChunk.Foreign -> chunk.lines.joinToString("\n")
                            }

                        div(
                            classes =
                                "px-4 py-2 border-b border-border last:border-b-0 whitespace-pre " +
                                    "w-max min-w-full " +
                                    if (own) "border-l-[3px] border-l-primary" else "",
                        ) { +text }
                    }
                }
            }
        }
    }
}
