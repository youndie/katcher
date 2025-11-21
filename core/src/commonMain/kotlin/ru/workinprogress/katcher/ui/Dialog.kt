package ru.workinprogress.katcher.ui

import kotlinx.html.ButtonType.button
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.onClick
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.unsafe

fun DIV.uiDialogContent(block: DIV.() -> Unit) {
    div(classes = "space-y-4 text-foreground") {
        block()
    }
}

fun HEAD.dialogScript() {
    script {
        unsafe {
            +
                """
                function closeDialogWithAnimation() {
    const modal = document.getElementById('modal-root');
    if (!modal) return;

    const backdrop = modal.querySelector('.dialog-backdrop');
    const win = modal.querySelector('.dialog-window');

    if (!backdrop || !win) {
        modal.innerHTML = "";
        return;
    }

    backdrop.classList.remove('animate-fadeIn');
    win.classList.remove('animate-scaleIn');

    backdrop.classList.add('animate-fadeOut');
    win.classList.add('animate-scaleOut');

    win.addEventListener('animationend', function handler() {
        win.removeEventListener('animationend', handler);
        modal.innerHTML = "";
    });
}
                """.trimIndent()
        }
    }
}

fun FlowContent.uiDialog(block: DIV.() -> Unit) {
    div(
        classes =
            "dialog-backdrop fixed inset-0 bg-[oklch(var(--background)/0.80)] backdrop-blur-sm z-50 flex " +
                "items-center justify-center animate-fadeIn",
    ) {
        div(
            classes =
                "dialog-window bg-background border border-border rounded-xl shadow-xl p-6 " +
                    "w-full max-w-md animate-scaleIn",
        ) {
            block()
        }
    }
}

fun DIV.uiDialogHeader(block: DIV.() -> Unit) {
    div(classes = "mb-4 flex justify-between items-center") { block() }
}

fun DIV.uiDialogTitle(text: String) {
    h2(classes = "text-lg font-semibold tracking-tight text-foreground") { +text }
}

fun DIV.uiDialogDescription(text: String) {
    p(classes = "text-sm text-muted") { +text }
}

fun DIV.uiDialogFooter(block: DIV.() -> Unit) {
    div(classes = "flex justify-end gap-2 mt-6") {
        block()
    }
}

fun DIV.uiDialogCloseButton() {
    button(classes = "text-muted hover:text-foreground transition") {
        type = button
        onClick = "closeDialogWithAnimation()"
        +"✕"
    }
}
