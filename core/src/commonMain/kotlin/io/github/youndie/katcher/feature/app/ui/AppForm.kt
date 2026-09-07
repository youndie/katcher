package io.github.youndie.katcher.feature.app.ui

import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.onClick
import io.github.youndie.katcher.feature.app.AppType
import io.github.youndie.katcher.feature.app.AppsResource
import io.github.youndie.katcher.ui.ButtonVariant
import io.github.youndie.katcher.ui.uiButton
import io.github.youndie.katcher.ui.uiDialog
import io.github.youndie.katcher.ui.uiDialogCloseButton
import io.github.youndie.katcher.ui.uiDialogContent
import io.github.youndie.katcher.ui.uiDialogFooter
import io.github.youndie.katcher.ui.uiDialogHeader
import io.github.youndie.katcher.ui.uiDialogTitle
import io.github.youndie.katcher.ui.uiInputField
import io.github.youndie.katcher.ui.uiOption
import io.github.youndie.katcher.ui.uiSelectField

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
