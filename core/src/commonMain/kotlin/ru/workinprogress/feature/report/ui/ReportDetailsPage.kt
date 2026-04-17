package ru.workinprogress.feature.report.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.*
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.report.Report
import ru.workinprogress.katcher.ui.ButtonSize
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.ui.uiButton
import ru.workinprogress.katcher.ui.uiCard
import ru.workinprogress.katcher.ui.uiCardHeader
import ru.workinprogress.katcher.ui.uiCardTitle
import ru.workinprogress.katcher.utils.human

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

    body("bg-background min-h-screen text-foreground transition-colors") {
        div("mx-auto max-w-5xl p-6 flex flex-col gap-4") {
            div("flex items-center justify-between") {
                val backUrl =
                    call.application.href(
                        AppsResource.AppId.Errors.GroupId(
                            appId = appId,
                            groupId = groupId,
                        ),
                    )

                uiButton(
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Sm,
                ) {
                    attributes.hx {
                        get = backUrl
                        pushUrl = "true"
                        target = "body"
                        swap = HxSwap.outerHtml
                    }
                    +"← Back to Reports"
                }

                div("text-xs text-muted-foreground font-mono") {
                    +"Report ID: ${report.id}"
                }
            }

            h1(classes = "text-2xl font-bold tracking-tight break-words mt-4 mb-4") { +report.message }

            uiCard {
                uiCardHeader {
                    uiCardTitle { +"Device & Environment" }
                }

                dl("grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4") {
                    infoRow("Release", report.release ?: "n/a")
                    infoRow("Environment", report.environment ?: "n/a")

                    report.context?.forEach { (key, value) ->
                        infoRow(key.replace("_", " ").capitalize(), value)
                    }
                }
            }

            if (!report.breadcrumbs.isNullOrEmpty()) {
                uiCard {
                    uiCardHeader {
                        uiCardTitle { +"Activity Timeline" }
                    }

                    div("relative border-l border-border/70 ml-2 space-y-8 py-1") {
                        report.breadcrumbs.forEach { breadcrumb ->
                            div("relative pl-6") {
                                div("absolute w-2.5 h-2.5 bg-primary rounded-full ring-4 ring-card -left-[5px] top-1.5") {}

                                div("flex flex-col gap-2") {
                                    div("flex items-start justify-between w-full gap-4") {
                                        div("flex items-baseline gap-2.5") {
                                            span("text-sm font-mono text-muted-foreground shrink-0") {
                                                +breadcrumb.timestamp.human()
                                            }
                                            span("text-muted-foreground/40 text-sm") { +"—" }
                                            h4("text-sm font-medium leading-snug text-foreground") {
                                                +breadcrumb.message
                                            }
                                        }

                                        span(
                                            "inline-flex items-center px-1.5 py-0.5 rounded-md bg-secondary text-secondary-foreground text-[10px] font-semibold uppercase tracking-wider shrink-0",
                                        ) {
                                            +breadcrumb.type
                                        }
                                    }

                                    breadcrumb.data?.let { data ->
                                        div("grid grid-cols-1 gap-1.5 p-2 bg-muted/40 rounded-lg border border-border/50") {
                                            data.forEach { (key, value) ->
                                                div("text-xs font-mono flex items-start gap-2") {
                                                    span("text-muted-foreground font-medium select-none") { +"$key:" }
                                                    span("text-foreground/90 break-all") { +value }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        div("relative pl-6") {
                            div("absolute w-2.5 h-2.5 bg-primary rounded-full ring-4 ring-card -left-[5px] top-1.5") {}

                            div("flex flex-col gap-2") {
                                div("flex items-start justify-between w-full gap-4") {
                                    div("flex items-baseline gap-2.5") {
                                        span("text-sm font-mono text-muted-foreground shrink-0") {
                                            +report.timestamp.human()
                                        }
                                        span("text-muted-foreground/40 text-sm") { +"—" }
                                        h4("text-sm font-medium leading-snug text-foreground") {
                                            +report.message
                                        }
                                    }

                                    span(
                                        "inline-flex items-center px-1.5 py-0.5 rounded-md bg-primary text-secondary-foreground text-[10px] font-semibold uppercase tracking-wider shrink-0",
                                    ) {
                                        +"Crash"
                                    }
                                }

                                reportStackTrace(report)
                            }
                        }
                    }
                }
            } else {
                reportStackTrace(report)
            }
        }
    }
}

private fun DIV.reportStackTrace(report: Report) {
    div("bg-zinc-950 rounded-xl shadow-md overflow-hidden border border-border") {
        div("px-4 py-2 bg-zinc-900/50 border-b border-zinc-800 flex justify-between items-center") {
            span("text-[10px] font-mono text-zinc-500 uppercase tracking-widest") { +"Stacktrace" }
        }
        div("p-4 overflow-x-auto") {
            pre("text-xs font-mono text-red-400/90 whitespace-pre-wrap leading-relaxed") {
                +report.stacktrace
            }
        }
    }
}

private fun DL.infoRow(
    label: String,
    value: String,
) {
    dl("flex flex-col gap-1") {
        dt("text-[10px] text-muted-foreground uppercase font-bold tracking-tight") { +label }
        dd("text-sm text-foreground font-mono break-all leading-tight") { +value }
    }
}
