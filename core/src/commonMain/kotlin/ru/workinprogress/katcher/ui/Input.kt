package ru.workinprogress.katcher.ui

import kotlinx.html.FlowContent
import kotlinx.html.INPUT
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.label

fun FlowContent.uiInputField(
    label: String,
    name: String,
    placeholder: String? = null,
) {
    div(classes = "flex flex-col gap-2") {
        label(classes = "text-sm font-medium") { +label }
        uiInput(name = name, placeholder = placeholder)
    }
}

fun FlowContent.uiInput(
    type: InputType = InputType.text,
    name: String? = null,
    value: String? = null,
    placeholder: String? = null,
    size: InputSize = InputSize.Default,
    extraClasses: String? = null,
    block: INPUT.() -> Unit = {},
) {
    val classes = inputClasses(size, extraClasses)

    input(classes = classes) {
        this.type = type
        name?.let { this.name = it }
        value?.let { this.value = it }
        placeholder?.let { this.placeholder = it }
        block()
    }
}

enum class InputSize {
    Default,
    Sm,
    Lg,
}

private fun inputClasses(
    size: InputSize,
    extra: String? = null,
): String {
    val base =
        "flex h-10 w-full rounded-md border border-input bg-background " +
            "px-3 py-2 text-sm ring-offset-background " +
            "placeholder:text-muted-foreground " +
            "focus-visible:outline-none focus-visible:ring-2 " +
            "focus-visible:ring-ring focus-visible:ring-offset-2 " +
            "disabled:cursor-not-allowed disabled:opacity-50"

    val sizeClasses =
        when (size) {
            InputSize.Default -> ""
            InputSize.Sm -> "h-8 px-2 text-xs"
            InputSize.Lg -> "h-12 px-4 text-base"
        }

    return listOfNotNull(base, sizeClasses, extra)
        .joinToString(" ")
}
