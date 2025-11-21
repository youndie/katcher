package ru.workinprogress.katcher.ui

import kotlinx.html.BUTTON
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button

fun FlowContent.uiButton(
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    extraClasses: String? = null,
    type: ButtonType = ButtonType.button,
    block: BUTTON.() -> Unit,
) {
    val classes = buttonClasses(variant, size, extraClasses)

    button(classes = classes) {
        this.type = type
        block()
    }
}

enum class ButtonVariant {
    Default,
    Outline,
    Ghost,
    Secondary,
    Destructive,
    Link,
}

enum class ButtonSize {
    Default,
    Sm,
    Lg,
    Icon,
}

private fun buttonClasses(
    variant: ButtonVariant,
    size: ButtonSize,
    extra: String? = null,
): String {
    val base =
        "inline-flex items-center justify-center whitespace-nowrap " +
            "rounded-md text-sm font-medium ring-offset-background " +
            "transition-colors focus-visible:outline-none " +
            "focus-visible:ring-2 focus-visible:ring-ring " +
            "focus-visible:ring-offset-2 disabled:pointer-events-none " +
            "disabled:opacity-50 cursor-pointer"

    val variantClasses =
        when (variant) {
            ButtonVariant.Default ->
                "bg-primary text-primary-foreground hover:bg-primary/90"

            ButtonVariant.Outline ->
                "border border-input bg-background hover:bg-accent hover:text-accent-foreground"

            ButtonVariant.Ghost ->
                "hover:bg-accent hover:text-accent-foreground"

            ButtonVariant.Secondary ->
                "bg-secondary text-secondary-foreground hover:bg-secondary/80"

            ButtonVariant.Destructive ->
                "bg-destructive text-destructive-foreground hover:bg-destructive/90"

            ButtonVariant.Link ->
                "text-primary underline-offset-4 hover:underline"
        }

    val sizeClasses =
        when (size) {
            ButtonSize.Default -> "h-9 px-4 py-2"
            ButtonSize.Sm -> "h-8 rounded-md px-3 text-xs"
            ButtonSize.Lg -> "h-10 rounded-md px-8"
            ButtonSize.Icon -> "h-9 w-9"
        }

    return listOfNotNull(base, variantClasses, sizeClasses, extra)
        .joinToString(" ")
}
