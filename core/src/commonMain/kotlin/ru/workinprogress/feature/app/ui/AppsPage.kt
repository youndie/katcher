package ru.workinprogress.feature.app.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.title
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.Icons.cloud
import ru.workinprogress.katcher.ui.Icons.logo
import ru.workinprogress.katcher.ui.commonHead
import ru.workinprogress.katcher.ui.uiButton

context(call: ApplicationCall)
fun HTML.appsPage(apps: List<App>) {
    head {
        title("Katcher – Apps")
        commonHead()
    }

    body(classes = "min-h-screen bg-background text-foreground") {
        div { id = "modal-root" }

        div(classes = "max-w-5xl mx-auto p-6 space-y-4 lg:pt-16") {
            div(classes = "flex justify-between mb-8 items-center") {
                div(classes = "flex items-center space-x-4 lg:space-x-6") {
                    logo()
                    h1(classes = "text-2xl lg:text-3xl font-semibold") { +"katcher" }
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
                        "sm:grid-cols-2",
                        "lg:grid-cols-3",
                        "gap-4",
                    )

                apps.forEach { app -> appCard(app) }
            }

            if (apps.isEmpty()) {
                emptyAppsView()
            }
        }
    }
}

context(call: ApplicationCall)
fun FlowContent.onAppCreated(app: App) {
    div {
        attributes.hx {
            swapOob = "beforeend:#apps-grid"
        }

        appCard(app)
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
            +"Create your first app to start sending error reports to katcher"
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
