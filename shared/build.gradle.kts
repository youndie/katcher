// `wasmJs` is still behind an opt-in in the Kotlin DSL (Kotlin 2.4.x).
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.multiplatform")
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
    id("ru.workinprogress.sborka.publish")
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()

    // Таргеты перечислены явно, а не выбираются по os.name: иначе в опубликованной версии
    // оказывается ровно один нативный вариант — тот, что подошёл машине сборки.
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    mingwX64()

    // A browser is a client of the ingest like any other, and `CreateReportParams` is the whole of
    // what it sends. Without this target a Kotlin/Wasm client declares its own copy of the wire
    // contract, which compiles for as long as the two stay in step and drops fields in silence
    // afterwards. Nothing in this module is platform-specific and all three dependencies publish
    // for wasmJs.
    wasmJs { browser() }
}

dependencies {
    commonMainImplementation(ktorLibs.client.resources)
    commonMainImplementation(libs.kotlinx.datetime)
    commonMainImplementation(libs.kotlinx.serialization.json)
}
