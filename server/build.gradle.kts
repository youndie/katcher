import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.pluginSerialization)
    alias(libs.plugins.kspPlugin)
}

ksp {
    arg("output-package", "ru.workinprogress.katcher.db")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    jvm()

    sourceSets["commonMain"].kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")

    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    val nativeTarget =
        when {
            hostOs == "Mac OS X" && arch == "x86_64" -> macosX64("native")
            hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("native")
            hostOs == "Linux" && (arch == "x86_64" || arch == "amd64") -> linuxX64("native")
            hostOs == "Linux" && arch == "aarch64" -> linuxArm64("native")
            hostOs.startsWith("Windows") -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

project.tasks.getByName("compileKotlinNative") {
    dependsOn("kspCommonMainKotlinMetadata")
}
project.tasks.getByName("compileKotlinJvm") {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks
    .matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }
    .configureEach {
        dependsOn("kspCommonMainKotlinMetadata")
    }

tasks.withType<KotlinCompilationTask<*>> {
    dependsOn("kspCommonMainKotlinMetadata")
}

// commonMain carries build/generated/ksp on its srcDirs, so every ktlint task reads the KSP
// output — and reading it before it is written is a race Gradle fails the build over. The
// format task already said this; the check task needs it just as much. What ktlint should
// make of those files is decided in .editorconfig, not here.
tasks.withType<BaseKtLintCheckTask>().configureEach {
    mustRunAfter(tasks.named("kspCommonMainKotlinMetadata"))
}

dependencies {
    add("kspCommonMainMetadata", libs.sqlx4k.codegen)

    commonMainImplementation(libs.sqlx4k.sqlite)

    commonMainImplementation(projects.core)
    commonMainImplementation(projects.shared)
    commonMainImplementation(projects.dev.retrace)

    commonMainImplementation(libs.kotlinx.datetime)
    commonMainImplementation(libs.okio)
    commonMainImplementation(libs.mcp.kotlin.sdk.server)
    commonMainImplementation(libs.metrik.agent)

    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(ktorLibs.server.di)
    commonMainImplementation(ktorLibs.server.auth)
    commonMainImplementation(ktorLibs.server.cio)
    commonMainImplementation(ktorLibs.server.resources)
    commonMainImplementation(ktorLibs.serialization.kotlinx.json)
    commonMainImplementation(ktorLibs.server.contentNegotiation)
    commonMainImplementation(ktorLibs.server.statusPages)
}
