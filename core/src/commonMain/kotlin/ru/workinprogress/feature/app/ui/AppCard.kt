package ru.workinprogress.feature.app.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import ru.workinprogress.feature.app.App
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.katcher.ui.Icons.copy

context(call: ApplicationCall)
fun FlowContent.appCard(app: App) {
    div(
        classes =
            "transition border border-border duration-300 hover:shadow-md hover:scale-[1.01] " +
                "cursor-pointer bg-card text-card-foreground pointer-events-auto",
    ) {
        attributes.hx {
            get =
                call.application.href(
                    AppsResource.AppId(appId = app.id),
                )
            trigger = "click"
            pushUrl = "true"
            target = "body"
            swap = HxSwap.outerHtml
        }

        div(classes = "p-4 border-b border-border flex items-center justify-between") {
            span(classes = "font-semibold") { +app.name }

            span(
                classes =
                    "text-xs px-2 py-1 rounded bg-secondary text-secondary-foreground capitalize",
            ) { text(app.type.name) }
        }

        div(classes = "p-4") {
            div(
                classes =
                    "text-xs font-mono bg-muted text-muted-foreground px-2 py-1 rounded " +
                        "flex items-center justify-between",
            ) {
                span(classes = "truncate") { +app.apiKey }

                button(
                    classes =
                        "ml-2 text-muted-foreground hover:text-foreground transition",
                ) {
                    id = "copy-btn-${app.id}"

                    attributes["onclick"] =
                        """
                        event.stopPropagation();
                        const icon = this.querySelector('.copy-icon');

                        const original = icon.innerHTML;
                        const successIcon = `
                            <svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                                <path d="M18 6h2v2h-2V6zm-2 4V8h2v2h-2zm-2 2v-2h2v2h-2zm-2 2h2v-2h-2v2zm-2 2h2v-2h-2v2zm-2 0v2h2v-2H8zm-2-2h2v2H6v-2zm0 0H4v-2h2v2z" fill="currentColor"/>
                            </svg>
                        `;

                        navigator.clipboard.writeText('${app.apiKey}').then(() => {
                            icon.innerHTML = successIcon;
                            icon.classList.remove('copy-pop');
                            void icon.offsetWidth; // restart animation
                            icon.classList.add('copy-pop');

                            setTimeout(() => {
                                icon.innerHTML = original;
                                icon.classList.remove('copy-pop');
                            }, 3000);
                        });
                        """.trimIndent()

                    div("w-4 h-4 copy-icon") {
                        copy()
                    }
                }
            }

            div { id = "copy-status-${app.id}" }
        }
    }
}
