package ru.workinprogress.katcher.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import ru.workinprogress.katcher.ui.Icons.check
import ru.workinprogress.katcher.ui.Icons.spinner

/**
 * A slot a fragment loads itself into.
 *
 * Three things belong to the slot rather than to the page around it: the request, the
 * spinner while it runs, and the message if it fails. A fragment that cannot load replaces
 * its own contents — the header and the controls above it stay usable, which is the point of
 * loading it separately in the first place.
 */
fun FlowContent.fragmentSlot(
    slotId: String,
    url: String,
    header: (FlowContent.() -> Unit)? = null,
    placeholder: (FlowContent.() -> Unit)? = null,
) {
    div(classes = "border border-border bg-card text-card-foreground") {
        id = slotId

        attributes.hx {
            get = url
            trigger = "load"
            target = "#$slotId-body"
            swap = HxSwap.innerHtml
        }
        attributes["hx-indicator"] = "#$slotId-spinner"
        // The only client-side branch on this page: a failed fragment has to say so, and the
        // server never learns that its response did not arrive.
        attributes["hx-on::response-error"] = "katcherFragmentError(this, event, '$slotId')"

        header?.let { block ->
            div(classes = "px-4 py-3 border-b border-border flex items-center justify-between gap-3") {
                block()

                span(classes = "htmx-indicator w-3.5 h-3.5 text-muted-foreground") {
                    id = "$slotId-spinner"
                    spinner()
                }
            }
        }

        div(classes = "fragment-dim") {
            id = "$slotId-body"
            placeholder?.invoke(this)
        }
    }
}

/** What a failed fragment shows instead of its rows: what was asked for, and a way to ask again. */
fun FlowContent.fragmentError(
    url: String,
    status: String,
    time: String,
    slotId: String,
) {
    div(classes = "px-4 py-4 flex items-center justify-between gap-4 flex-wrap") {
        div(classes = "flex flex-col gap-1 min-w-0") {
            div(classes = "text-sm font-medium") { +"This list could not be loaded" }
            div(classes = "text-xs font-mono text-muted-foreground break-all") {
                +"GET $url — $status, $time"
            }
        }

        uiButton(variant = ButtonVariant.Outline, size = ButtonSize.Sm) {
            attributes.hx {
                get = url
                target = "#$slotId-body"
                swap = HxSwap.innerHtml
            }
            +"Retry"
        }
    }
}

/**
 * A short notice in the corner, sent as an out-of-band swap so any action can raise one
 * without owning the place it appears in.
 */
fun FlowContent.toast(
    message: String,
    undo: (FlowContent.() -> Unit)? = null,
) {
    div {
        id = "toast"
        attributes.hx { swapOob = "true" }

        div(
            classes =
                "katcher-toast fixed bottom-4 right-4 z-50 inline-flex items-center gap-2.5 " +
                    "bg-card text-card-foreground border border-input px-3 py-2.5 shadow-lg",
        ) {
            span(classes = "w-3.5 h-3.5 text-primary") { check() }
            span(classes = "text-[13px]") { +message }
            undo?.invoke(this)
        }
    }
}

/** An empty `#toast` — what an action answers with when it has nothing to announce. */
fun FlowContent.toastSlot() {
    div { id = "toast" }
}
