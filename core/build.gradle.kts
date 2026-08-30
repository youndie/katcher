plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// NOT A LIBRARY: nothing publishes or resolves this module, so there is no consumer for a
// spelled-out public API to be spelled out for.
kotlin {
    explicitApi = null
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }

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
    commonMainImplementation(projects.shared)
    commonMainImplementation(projects.dev.retrace)
    commonMainImplementation(libs.kotlinx.datetime)

    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(ktorLibs.server.resources)
    commonMainImplementation(ktorLibs.serialization.kotlinx.json)
    commonMainImplementation(ktorLibs.server.contentNegotiation)
    commonMainImplementation(ktorLibs.server.statusPages)
    commonMainImplementation(ktorLibs.server.auth)
    commonMainImplementation(kotlinCrypto.hash.sha2)
    commonMainImplementation(libs.okio)

    commonMainImplementation(ktorLibs.server.htmx)
    commonMainImplementation(ktorLibs.htmx)
    commonMainImplementation(ktorLibs.htmx.html)
    commonMainImplementation(ktorLibs.server.htmlBuilder)
}
