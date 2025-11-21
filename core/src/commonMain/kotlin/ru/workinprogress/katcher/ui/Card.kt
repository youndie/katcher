package ru.workinprogress.katcher.ui

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.H3
import kotlinx.html.P
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.p

fun FlowContent.uiCard(block: DIV.() -> Unit) =
    div(
        classes =
            "bg-card text-card-foreground rounded-xl " +
                "border border-border shadow-sm p-4",
    ) { block() }

fun FlowContent.uiCardHeader(block: DIV.() -> Unit) = div(classes = "mb-3") { block() }

fun FlowContent.uiCardTitle(block: H3.() -> Unit) = h3(classes = "text-lg font-semibold tracking-tight") { block() }

fun FlowContent.uiCardDescription(block: P.() -> Unit) = p(classes = "text-sm text-muted-foreground") { block() }

fun FlowContent.uiCardContent(block: DIV.() -> Unit) = div { block() }

fun FlowContent.infoRow(
    name: String,
    value: String,
) = div(classes = "flex justify-between") {
    div(classes = "text-muted-foreground") { +name }

    div(classes = "font-medium text-foreground") { +value }
}
