package ru.workinprogress.feature.app.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.input
import kotlinx.html.onClick
import kotlinx.html.p
import kotlinx.html.span
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppContents
import ru.workinprogress.feature.app.AppKey
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
import ru.workinprogress.katcher.utils.ageWords
import ru.workinprogress.katcher.utils.maskKey

context(call: ApplicationCall)
fun FlowContent.appRenameModal(app: App) {
    uiDialog {
        uiDialogHeader {
            uiDialogTitle("Rename ${app.name}")
            uiDialogCloseButton()
        }

        uiDialogContent {
            form {
                attributes.hx {
                    post = call.application.href(AppsResource.AppId.Rename(appId = app.id))
                    target = "#app-card-${app.id}"
                    swap = HxSwap.outerHtml
                }
                attributes["onsubmit"] = "closeDialogWithAnimation()"

                div(classes = "space-y-2") {
                    uiInputField("App name", "name")
                }

                div {
                    uiDialogFooter {
                        uiButton(variant = ButtonVariant.Ghost) {
                            onClick = "closeDialogWithAnimation()"
                            +"Cancel"
                        }
                        uiButton(variant = ButtonVariant.Default, type = ButtonType.submit) { +"Rename" }
                    }
                }
            }
        }
    }
}

/**
 * Reissuing is reversible and costs nothing, so it asks plainly rather than demanding proof.
 * What it does say is that the current key keeps working — otherwise nobody would dare press
 * it on an app that is shipping.
 */
context(call: ApplicationCall)
fun FlowContent.appReissueModal(
    app: App,
    currentKey: AppKey?,
    now: Long,
) {
    uiDialog {
        uiDialogHeader {
            uiDialogTitle("Reissue key for ${app.name}")
            uiDialogCloseButton()
        }

        uiDialogContent {
            p(classes = "text-[13px] leading-relaxed text-muted-foreground") {
                +(
                    "A new key is issued and shown on the card. The current key keeps working " +
                        "until you revoke it, so shipped builds keep reporting."
                )
            }

            currentKey?.let { key ->
                div(classes = "text-xs font-mono text-muted-foreground border-l-2 border-secondary pl-2.5") {
                    +(
                        "current key " + maskKey(key.key) + " · " +
                            (
                                key.lastUsedAt?.let { used -> "last used " + ageWords((now - used).coerceAtLeast(0)) }
                                    ?: "never used"
                            )
                    )
                }
            }

            uiDialogFooter {
                uiButton(variant = ButtonVariant.Ghost) {
                    onClick = "closeDialogWithAnimation()"
                    +"Cancel"
                }

                uiButton(variant = ButtonVariant.Default) {
                    attributes.hx {
                        post = call.application.href(AppsResource.AppId.Keys(appId = app.id))
                        target = "#app-key-${app.id}"
                        swap = HxSwap.outerHtml
                    }
                    attributes["onclick"] = "closeDialogWithAnimation()"
                    +"Reissue"
                }
            }
        }
    }
}

/**
 * Deleting is not reversible and takes the reports with it, so the dialog says the numbers
 * and asks for the name to be typed. The button stays disabled until it matches — a confirm
 * dialog nobody reads is a confirm dialog that does not confirm anything.
 */
context(call: ApplicationCall)
fun FlowContent.appDeleteModal(
    app: App,
    contents: AppContents,
) {
    uiDialog {
        uiDialogHeader {
            uiDialogTitle("Delete ${app.name}")
            uiDialogCloseButton()
        }

        uiDialogContent {
            p(classes = "text-[13px] leading-relaxed text-muted-foreground") {
                +(
                    "This removes the app, its ${countWords(contents.groups, "group")} and " +
                        "${countWords(contents.reports, "report")}. The key stops being accepted " +
                        "immediately. It cannot be undone."
                )
            }

            div(classes = "space-y-1.5") {
                span(classes = "text-xs text-muted-foreground") { +"Type the app name to confirm" }
                input(type = InputType.text, classes = CONFIRM_INPUT_CLASSES) {
                    attributes["id"] = "delete-confirm-${app.id}"
                    placeholder = app.name
                    attributes["autocomplete"] = "off"
                    attributes["oninput"] =
                        "document.getElementById('delete-submit-${app.id}').disabled = " +
                        "this.value !== this.placeholder"
                }
            }

            uiDialogFooter {
                uiButton(variant = ButtonVariant.Ghost) {
                    onClick = "closeDialogWithAnimation()"
                    +"Cancel"
                }

                uiButton(variant = ButtonVariant.Destructive) {
                    attributes["id"] = "delete-submit-${app.id}"
                    attributes["disabled"] = "disabled"
                    attributes.hx {
                        delete = call.application.href(AppsResource.AppId(appId = app.id))
                        target = "#app-card-${app.id}"
                        swap = HxSwap.outerHtml
                    }
                    attributes["onclick"] = "closeDialogWithAnimation()"
                    +"Delete app"
                }
            }
        }
    }
}

private fun countWords(
    count: Int,
    noun: String,
): String = if (count == 1) "1 $noun" else "$count ${noun}s"

private const val CONFIRM_INPUT_CLASSES =
    "h-9 w-full box-border border border-input bg-background text-foreground px-2.5 text-sm rounded-none"
