package ru.workinprogress.feature.error.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.HTML
import kotlinx.html.TR
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.ErrorGroupsPaginated
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder
import ru.workinprogress.katcher.ui.ButtonSize
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.Icons.bug
import ru.workinprogress.katcher.ui.Icons.check
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.ui.uiButton
import ru.workinprogress.katcher.utils.human

context(call: ApplicationCall)
fun HTML.appErrorsPage(app: App) {
    head {
        title("Errors — ${app.name}")
        commonHead()
    }

    body(classes = "bg-background text-foreground min-h-screen") {
        div(classes = "mx-auto max-w-5xl p-6 space-y-6") {
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
                +"← Back"
            }

            h1(classes = "text-3xl font-bold tracking-tight") { +app.name }

            div(
                classes =
                    "bg-card text-card-foreground shadow-lg p-4 rounded-xl " +
                        "border border-border",
            ) {
                id = "errors-table"

                attributes.hx {
                    get =
                        call.application.href(
                            AppsResource.AppId.Errors(
                                parent = AppsResource.AppId(appId = app.id),
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
                    +"No errors yet"
                }

                p(classes = "text-muted-foreground max-w-sm") {
                    +"Init Katcher in your application and start sending errors to see them here."
                }

                pre(
                    classes = "font-mono text-sm bg-muted p-4 rounded-lg text-left whitespace-pre-wrap leading-relaxed max-w-sm",
                ) {
                    +
                        """
                        try {
                            riskyCode()
                        } catch (t: Throwable) {
                            Katcher.catch(t)
                        }
                        """.trimIndent()
                }
            }
        }

        if (data.items.isNotEmpty()) {
            table(classes = "w-full min-w-max") {
                thead(classes = "bg-muted text-muted-foreground border-b border-border") {
                    tr {
                        th(classes = "p-2 w-8") { }

                        headerCell(appId, ErrorGroupSort.title, "Message", data)
                        headerCell(appId, ErrorGroupSort.occurrences, "Count", data)
                        headerCell(
                            appId,
                            ErrorGroupSort.lastSeen,
                            "Last seen",
                            data,
                            extraClasses = "w-[130px] min-w-[130px] whitespace-nowrap",
                        )
                    }
                }

                tbody {
                    data.items.forEach { group ->

                        tr(
                            classes =
                                "${if (group.viewed) "text-foreground/60" else ""} " +
                                    "border-b border-border cursor-pointer " +
                                    "hover:bg-accent hover:text-accent-foreground transition",
                        ) {
                            attributes.hx {
                                get =
                                    call.application.href(
                                        AppsResource.AppId.Errors.GroupId(
                                            parent = AppsResource.AppId.Errors(parent = AppsResource.AppId(appId = appId)),
                                            groupId = group.errorGroup.id,
                                        ),
                                    )
                                pushUrl = "true"
                                target = "body"
                                swap = HxSwap.outerHtml
                            }

                            td(classes = "px-2 pb-6 w-8") {
                                if (group.errorGroup.resolved) {
                                    div("w-6 h-6 text-muted-foreground") {
                                        check()
                                    }
                                }
                            }

                            td(classes = "p-2 whitespace-normal break-words max-w-[300px]") {
                                +group.errorGroup.title
                            }
                            td(classes = "p-2") { text(group.errorGroup.occurrences) }
                            td(classes = "p-2 w-[130px] min-w-[130px] whitespace-nowrap") {
                                +group.errorGroup.lastSeen.human()
                            }
                        }
                    }
                }
            }
        }

        div(classes = "flex gap-2 mt-4") {
            if (data.page > 1) {
                uiButton(variant = ButtonVariant.Outline) {
                    attributes.hx {
                        get =
                            call.application.href(
                                AppsResource.AppId.Errors.Paginated(
                                    parent = AppsResource.AppId.Errors(parent = AppsResource.AppId(appId = appId)),
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
                                    parent = AppsResource.AppId.Errors(parent = AppsResource.AppId(appId = appId)),
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

context(call: ApplicationCall)
fun TR.headerCell(
    appId: Int,
    field: ErrorGroupSort,
    label: String,
    data: ErrorGroupsPaginated,
    extraClasses: String = "",
) {
    th(
        classes =
            "p-2 text-left cursor-pointer " +
                "hover:bg-accent hover:text-accent-foreground transition " +
                extraClasses,
    ) {
        attributes.hx {
            get =
                call.application.href(
                    AppsResource.AppId.Errors.Paginated(
                        parent = AppsResource.AppId.Errors(parent = AppsResource.AppId(appId = appId)),
                        sortBy = field,
                        sortOrder =
                            if (data.sortBy == field &&
                                data.sortOrder == ErrorGroupSortOrder.asc
                            ) {
                                ErrorGroupSortOrder.desc
                            } else {
                                ErrorGroupSortOrder.asc
                            },
                    ),
                )
            target = "#errors-table"
            swap = HxSwap.innerHtml
        }

        +label
    }
}
