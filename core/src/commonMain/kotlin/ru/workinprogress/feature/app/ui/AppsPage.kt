package ru.workinprogress.feature.app.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.title
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppOverview
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.Icons.cloud
import ru.workinprogress.katcher.ui.Icons.logo
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.ui.toastSlot
import ru.workinprogress.katcher.ui.uiButton

context(call: ApplicationCall)
fun HTML.appsPage(
    apps: List<App>,
    overviews: Map<Int, AppOverview>,
    now: Long,
) {
    head {
        title("Katcher – Apps")
        commonHead()
    }

    body(classes = "min-h-screen bg-background text-foreground") {
        div { id = "modal-root" }
        toastSlot()

        div(classes = "max-w-5xl mx-auto p-6 space-y-4 lg:pt-16") {
            div(classes = "flex justify-between mb-8 items-center gap-3 flex-wrap") {
                div(classes = "flex items-center space-x-4 lg:space-x-6") {
                    logo()
                    h1(classes = "text-2xl lg:text-3xl font-semibold") { +"katcher" }

                    if (apps.isNotEmpty()) {
                        span(classes = "text-[13px] font-mono text-muted-foreground pt-1") {
                            +appsSummary(apps, overviews)
                        }
                    }
                }

                uiButton(variant = ButtonVariant.Outline) {
                    attributes.hx {
                        get =
                            call.application.href(
                                AppsResource.Form(),
                            )
                        target = "#modal-root"
                        swap = HxSwap.innerHtml
                    }
                    +"Add app"
                }
            }

            div {
                id = "apps-grid"
                classes =
                    setOf(
                        "grid",
                        "grid-cols-1",
                        "lg:grid-cols-2",
                        "gap-4",
                    )

                apps.forEach { app ->
                    appCard(app, overviews[app.id] ?: AppOverview.silent(app.id), now)
                }
            }

            if (apps.isEmpty()) {
                emptyAppsView()
            }
        }
    }
}

context(call: ApplicationCall)
fun FlowContent.onAppCreated(
    app: App,
    now: Long,
) {
    div {
        attributes.hx {
            swapOob = "beforeend:#apps-grid"
        }

        // A card created a second ago has nothing behind it yet; the silent overview is the
        // honest one, and it renders as "never reported".
        appCard(app, AppOverview.silent(app.id), now)
    }

    div {
        attributes.hx { swapOob = "true" }
        id = "empty-view"
    }

    div {
        id = "modal-root"
        attributes.hx { swapOob = "true" }
    }
}

context(call: ApplicationCall)
private fun FlowContent.emptyAppsView() {
    div(classes = "flex flex-col items-center justify-center py-20 text-center space-y-6") {
        id = "empty-view"

        div(
            classes = "w-16 h-16 p-4 rounded-full bg-muted flex items-center justify-center",
        ) {
            cloud()
        }

        h2(classes = "text-xl font-semibold") {
            +"No apps yet"
        }

        p(classes = "text-muted-foreground max-w-sm") {
            +"Create an app to get a key. The key goes into "
            code(classes = "font-mono text-foreground") { +"Katcher.start { }" }
            +", and crashes start arriving."
        }

        uiButton(variant = ButtonVariant.Default) {
            attributes.hx {
                get = call.application.href(AppsResource.Form())
                target = "#modal-root"
                swap = HxSwap.innerHtml
            }
            +"Add app"
        }
    }
}

/**
 * The line next to the logo. Quiet is counted, not coloured: it belongs in the same sentence
 * as the total, so a list of silent apps reads as a fact rather than as an alarm.
 */
private fun appsSummary(
    apps: List<App>,
    overviews: Map<Int, AppOverview>,
): String {
    val quiet = apps.count { app -> (overviews[app.id]?.crashes24h ?: 0) == 0 }
    val appsWord = if (apps.size == 1) "1 app" else "${apps.size} apps"

    return if (quiet == 0) appsWord else "$appsWord · $quiet quiet"
}
