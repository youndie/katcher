package ru.workinprogress.feature.app.ui

import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.onClick
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.katcher.ui.ButtonVariant
import ru.workinprogress.katcher.ui.uiButton
import ru.workinprogress.katcher.ui.uiDialog
import ru.workinprogress.katcher.ui.uiDialogCloseButton
import ru.workinprogress.katcher.ui.uiDialogContent
import ru.workinprogress.katcher.ui.uiDialogFooter
import ru.workinprogress.katcher.ui.uiDialogHeader
import ru.workinprogress.katcher.ui.uiDialogTitle
import ru.workinprogress.katcher.ui.uiInputField
import ru.workinprogress.katcher.ui.uiOption
import ru.workinprogress.katcher.ui.uiSelectField

context(call: ApplicationCall)
fun FlowContent.appCreateModal() {
    uiDialog {
        uiDialogHeader {
            uiDialogTitle("Create App")
            uiDialogCloseButton()
        }

        uiDialogContent {
            form {
                attributes.hx {
                    post =
                        call.application.href(
                            AppsResource(),
                        )
                    target = "#apps-grid"
                    swap = "beforeend"
                }

                div(classes = "space-y-2") {
                    uiInputField("App name", "name")

                    uiSelectField("Type", "type") {
                        for (type in AppType.entries) {
                            uiOption(type.name)
                        }
                    }
                }

                div {
                    uiDialogFooter {
                        uiButton(variant = ButtonVariant.Link) {
                            onClick = "closeDialogWithAnimation()"
                            +"Cancel"
                        }

                        uiButton(variant = ButtonVariant.Default, type = ButtonType.submit) {
                            +"Create"
                        }
                    }
                }
            }
        }
    }
}
