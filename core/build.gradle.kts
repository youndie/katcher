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
        // `-Xcontext-parameters` is gone: context parameters are on by default at language version
        // 2.4, and the compiler says so — "the argument is redundant for the current language
        // version". It said so before this migration too; `allWarningsAsErrors`, which the shared
        // conventions turn on, is what turned saying into failing.

        // OPTED IN OUT LOUD. Ktor's HTMX DSL is experimental, and this module is built on it — 183
        // warnings from one file alone. Saying it once here is the statement `@OptIn` would make at
        // every use site, and it is an honest one: the pages in this module will need rewriting when
        // that DSL changes.
        optIn.add("io.ktor.utils.io.ExperimentalKtorApi")
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
