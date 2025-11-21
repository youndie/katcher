package ru.workinprogress.katcher.ui

import kotlinx.html.FlowContent
import kotlinx.html.SELECT
import kotlinx.html.div
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.unsafe

fun FlowContent.uiSelectField(
    label: String,
    name: String,
    block: SELECT.() -> Unit,
) {
    div {
        label(classes = "block mb-1 text-sm font-medium") { +label }
        uiSelect(name = name) {
            block()
        }
    }
}

fun FlowContent.uiSelect(
    name: String? = null,
    size: SelectSize = SelectSize.Default,
    extraClasses: String? = null,
    block: SELECT.() -> Unit,
) {
    val wrapperClasses =
        "relative inline-flex items-center w-full"

    div(classes = wrapperClasses) {
        val classes =
            selectClasses(size, extraClasses) +
                " appearance-none pr-10"

        select(classes = classes) {
            name?.let { this.name = it }
            block()
        }

        div(classes = "pointer-events-none absolute right-3 text-gray-500") {
            unsafe {
                +
                    """
                    <svg xmlns="http://www.w3.org/2000/svg" 
                         width="16" height="16"
                         fill="none" viewBox="0 0 24 24" 
                         stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" 
                            d="M19 9l-7 7-7-7" />
                    </svg>
                    """.trimIndent()
            }
        }
    }
}

enum class SelectSize {
    Default,
    Sm,
    Lg,
}

private fun selectClasses(
    size: SelectSize,
    extra: String? = null,
): String {
    val base =
        "flex h-10 w-full items-center justify-between rounded-md border " +
            "border-input bg-background px-3 py-2 text-sm ring-offset-background " +
            "focus:outline-none focus:ring-2 focus:ring-ring " +
            "focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"

    val sizeClasses =
        when (size) {
            SelectSize.Default -> ""
            SelectSize.Sm -> "h-8 px-2 text-xs"
            SelectSize.Lg -> "h-12 px-4 text-base"
        }

    return listOfNotNull(base, sizeClasses, extra)
        .joinToString(" ")
}

fun SELECT.uiOption(
    value: String,
    selected: Boolean = false,
    label: String = value,
) {
    option {
        this.value = value
        if (selected) this.selected = true
        +label
    }
}
