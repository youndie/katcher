package ru.workinprogress.feature.app.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import ru.workinprogress.feature.app.AppsResource

/**
 * The ⋯ menu of a card: rename, reveal, reissue, delete.
 *
 * Open and closed are two responses rather than a class the client toggles. That costs a
 * request and buys the rule this interface is built on — every piece of state on the screen
 * came from the server that owns it.
 */
context(call: ApplicationCall)
fun FlowContent.appMenuButton(
    appId: Int,
    opensTo: Boolean = true,
) {
    button(
        classes =
            "w-7 h-7 inline-flex items-center justify-center border border-border " +
                "text-muted-foreground hover:text-foreground transition cursor-pointer",
    ) {
        attributes["onclick"] = "event.stopPropagation();"
        attributes.hx {
            // The same button both opens and closes: while the menu is up it asks for the
            // closed state, so pressing ⋯ twice leaves the card as it was found.
            get = call.application.href(AppsResource.AppId.Menu(appId = appId, open = opensTo))
            target = "#app-menu-$appId"
            swap = HxSwap.outerHtml
            pushUrl = "false"
        }
        +"⋯"
    }
}

context(call: ApplicationCall)
fun FlowContent.appMenu(appId: Int) {
    div(classes = "relative") {
        attributes["onclick"] = "event.stopPropagation();"

        appMenuButton(appId, opensTo = false)

        // A click anywhere else closes it. An open menu with no way out but the one button
        // that opened it is a trap, and this is the version of "click outside" that needs no
        // client-side state: a transparent sheet that asks the server for the closed menu.
        div(classes = "fixed inset-0 z-30") {
            attributes.hx {
                get = call.application.href(AppsResource.AppId.Menu(appId = appId, open = false))
                target = "#app-menu-$appId"
                swap = HxSwap.outerHtml
                pushUrl = "false"
            }
        }

        div(
            classes =
                "absolute right-0 top-8 z-40 w-52 border border-input bg-card text-card-foreground shadow-lg",
        ) {
            menuItem(
                appId = appId,
                label = "Rename",
                url = call.application.href(AppsResource.AppId.Rename(appId = appId)),
                target = "#modal-root",
            )
            menuItem(
                appId = appId,
                label = "Reveal key",
                url = call.application.href(AppsResource.AppId.Key(appId = appId)),
                target = "#app-key-$appId",
            )
            menuItem(
                appId = appId,
                label = "Reissue key",
                url = call.application.href(AppsResource.AppId.Reissue(appId = appId)),
                target = "#modal-root",
            )
            menuItem(
                appId = appId,
                label = "Delete app",
                url = call.application.href(AppsResource.AppId.Delete(appId = appId)),
                target = "#modal-root",
                danger = true,
            )
        }
    }
}

context(call: ApplicationCall)
private fun FlowContent.menuItem(
    appId: Int,
    label: String,
    url: String,
    target: String,
    danger: Boolean = false,
) {
    div(
        classes =
            "px-3 py-2.5 text-[13px] cursor-pointer border-b border-border last:border-b-0 " +
                "hover:bg-accent hover:text-accent-foreground transition " +
                if (danger) "text-primary" else "",
    ) {
        attributes.hx {
            get = url
            this.target = target
            swap = if (target == "#modal-root") HxSwap.innerHtml else HxSwap.outerHtml
            // A menu item opens a dialog or swaps a row; neither is a page to come back to.
            pushUrl = "false"
        }
        // Whatever the item did, the menu has done its job and closes.
        attributes["hx-on::after-request"] =
            "htmx.ajax('GET', '" +
            call.application.href(AppsResource.AppId.Menu(appId = appId, open = false)) +
            "', {target: '#app-menu-$appId', swap: 'outerHTML'})"

        span { +label }
    }
}
