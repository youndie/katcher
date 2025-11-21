plugins {
    alias(libs.plugins.pluginSerialization)
    `maven-publish`
    kotlin("multiplatform")
}

group = "ru.workinprogress.katcher"
version = libVersion()

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/youndie/katcher")
            credentials {
                username = project.findProperty("PKG_USER") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("PKG_SECRET") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}

kotlin {
    withSourcesJar()
    jvmToolchain(21)

    jvm()

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
    }
}

dependencies {
    commonMainApi(projects.shared)
    commonMainImplementation(ktorLibs.client.core)
    commonMainImplementation(ktorLibs.client.contentNegotiation)
    commonMainImplementation(ktorLibs.serialization.kotlinx.json)
    commonMainImplementation(libs.kotlinx.serialization.json)
}

fun Project.libVersion(): String = findProperty("VERSION")?.toString() ?: ("0.0." + (findProperty("BUILD_NUMBER") ?: "snapshot"))
