import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.pluginSerialization) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(jvmLibs.plugins.jib) apply false
    alias(jvmLibs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ktlintPlugin)
    // id("ru.workinprogress.katcher.gradle.plugin") apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    version = libVersion()
    group = "ru.workinprogress.katcher"

    repositories {
        mavenCentral()
        // Агент metrik публикуется сюда, в Central его нет. Объявлять надо именно здесь:
        // repositories у подпроектов перекрывают то, что задано в settings.gradle.kts.
        maven("https://reposilite.kotlin.website/snapshots") { name = "WipSnapshots" }
    }

    configure<KtlintExtension> {
        debug.set(true)
        // renovate: datasource=maven depName=com.pinterest.ktlint:ktlint-cli
        version = "1.8.0"
    }
}

fun Project.libVersion(): String = findProperty("VERSION")?.toString() ?: ("0.1." + (findProperty("BUILD_NUMBER") ?: "snapshot"))
