plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
}

// NOT A LIBRARY: nothing publishes or resolves this module, so there is no consumer for a
// spelled-out public API to be spelled out for.
kotlin {
    explicitApi = null
}

// THE CLIENT AS IT PUBLISHES, WHEN ASKED. `-Pkatcher.sample.client=<version>` resolves the coordinate
// from Maven Local instead of the project, which is what the `Image` workflow does after
// `publishToMavenLocal` of the same commit (B-03): a project dependency would hide a broken
// publication, and the publication is the half a consumer gets. Maven Local itself is declared in
// `settings.gradle.kts`: a `repositories { }` block here would, under `PREFER_PROJECT`, replace the
// settings' list, and with it the repository the patched Kotlin/Native compiler comes from.
val publishedClient: String? = providers.gradleProperty("katcher.sample.client").orNull

// THE NEGATIVE CONTROL. `-Pkatcher.sample.engine=none` links no Ktor engine, the way a consumer that
// forgot one does; the client ships none of its own. The workflow requires such a binary to refuse at
// `start`, saying why, rather than to keep a report on disk and retry it in silence.
val linkEngine: Boolean = providers.gradleProperty("katcher.sample.engine").orNull != "none"

kotlin {
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
        named("nativeMain") {
            dependencies {
                if (publishedClient == null) {
                    implementation(projects.client)
                } else {
                    implementation("io.github.youndie.katcher:client:$publishedClient")
                }
                implementation(libs.kotlinx.coroutines.core)
                if (linkEngine) implementation(ktorLibs.client.cio)
            }
        }
    }
}
