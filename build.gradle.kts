import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.pluginSerialization) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(jvmLibs.plugins.jib) apply false
    alias(jvmLibs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ktlintPlugin)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    version = libVersion()
    group = "ru.workinprogress.katcher"

    repositories {
        mavenCentral()
    }

    configure<KtlintExtension> {
        debug.set(true)
        version = "1.8.0"
    }
}

fun Project.libVersion(): String = findProperty("VERSION")?.toString() ?: ("0.1." + (findProperty("BUILD_NUMBER") ?: "snapshot"))
