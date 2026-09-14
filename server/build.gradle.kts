import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
    id("io.github.youndie.sborka.parity")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    // Generates `KoreBuildIdentity` — the version, the commit and the build time, as compiled-in
    // source. Kotlin/Native has neither resources nor a manifest, so `/version` has no other way to
    // know what it is serving. The version it stamps is this project's, which is `version` in
    // gradle.properties, which is the same head the image tag and both publish workflows spell.
    alias(libs.plugins.koreBuild)
}

// NOT A LIBRARY: nothing publishes or resolves this module, so there is no consumer for a
// spelled-out public API to be spelled out for.
kotlin {
    explicitApi = null
}

// WHERE THE PLATFORM PROBE LOOKS. `localhost` is the name it resolves; the port is bound by the test
// itself, so the suite needs nothing running beside it. See `PlatformTest` for what that covers and
// what it does not.
parityProbe {
    host = "localhost"
    port = 0
}

ksp {
    arg("output-package", "io.github.youndie.katcher.db")
}

kotlin {
    compilerOptions {
        // `-Xcontext-parameters` is gone: context parameters are on by default at language version
        // 2.4, and the compiler says so — "the argument is redundant for the current language
        // version". It said so before this migration too; `allWarningsAsErrors`, which the shared
        // conventions turn on, is what turned saying into failing.
    }

    jvm()

    sourceSets["commonMain"].kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")

    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    val isLinuxX64Host = hostOs == "Linux" && (arch == "x86_64" || arch == "amd64")
    val nativeTarget =
        when {
            hostOs == "Mac OS X" && arch == "x86_64" -> macosX64("native")
            hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("native")
            hostOs == "Linux" && (arch == "x86_64" || arch == "amd64") -> linuxX64("native")
            hostOs == "Linux" && arch == "aarch64" -> linuxArm64("native")
            hostOs.startsWith("Windows") -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

    // A LINUX BUILD LINKS STATICALLY, so the runtime image needs no base image (#55). glibc,
    // libstdc++ and libgcc move inside the binary — it grows by 893 312 bytes — and the 10 643 700-byte
    // distroless/cc layer under it disappears. Measured on the finished images: 15 542 820 bytes to
    // pull becomes 9 570 311.
    //
    // STATIC HERE DOES NOT MEAN SELF-CONTAINED. glibc's `iconv` loads its converters with `dlopen`,
    // and a static binary that calls it still needs the shared glibc and the gconv modules on disk
    // — which is why `server/Dockerfile` copies five things across and is not a `FROM scratch` with
    // one COPY. Read that file before changing this one; the two are one decision.
    //
    // The link needs the static archives: `libc.a`, `crt1.o`, `libstdc++.a`, `libgcc.a`,
    // `libgcc_eh.a`. `apt-get install g++` supplies all of them; the stock `gradle:…-noble` image
    // carries none, and the failure there reads `unable to find library -lc`, which looks like a
    // linker-flag problem and is not one. `-Pkatcher.staticLink=false` is the way out on a machine
    // without g++ — the binary then links as it always did.
    //
    // `-Xoverride-konan-properties` IS NOT A STABLE INTERFACE. Five keys are pinned below and
    // JetBrains have said they may change in any patch release, so a Kotlin bump can break this. It
    // breaks loudly, as a link error, and the `Image` workflow is what makes a pull request rather
    // than a release the place that happens.
    //
    // `linkerKonanFlags` is THE STOCK VALUE WITH `-Bdynamic` REMOVED and nothing else changed — read
    // the key in `konan.properties` before editing it, its value continues onto a second line.
    // Rewriting it from memory drops `--gc-sections` and costs 316 488 bytes for nothing.
    //
    // Two of the five overrides exist only because `-linker-option -static` does not currently mean
    // static upstream: KT-89362, patch in JetBrains/kotlin#8127. If that lands, `--no-dynamic-linker`
    // and the `linkerKonanFlags` line go away.
    //
    // The gcc directory is pinned to 13 — what noble ships and what the builder image has. A host
    // with another gcc fails the link naming the path it could not find, which is the readable half
    // of a trade against globbing the filesystem at configuration time.
    val staticLinux =
        isLinuxX64Host && (project.findProperty("katcher.staticLink") as String?)?.toBoolean() != false

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"

                // 16 KiB PAGES INSTEAD OF THE DEFAULT 256, AND THIS IS WHAT KEEPS THE PROCESS ALIVE
                // UNDER ITS MEMORY LIMIT (#58). Kotlin/Native's `CustomAllocator` is per-thread:
                // every thread that touches a block-size class keeps a page of that class for as
                // long as it lives, occupied or not, so the resident set tracks the THREAD COUNT
                // rather than the live heap — and `Dispatchers.IO` grows threads on demand under
                // concurrency. No GC setting bounds it, because these are pages, not objects.
                //
                // Measured here, on this service, in the image the chart deploys, under
                // `--memory=192m --cpus=1` and fifty concurrent navigators over `/apps` and the
                // error pages:
                //
                //                 idle RSS      peak RSS at 1 GiB     survived 192Mi
                //   default       56–68 MB        252–329 MB               0 / 8
                //   this          22–26 MB         47–62 MB                8 / 8
                //   allocator=std 21–22 MB         85–161 MB               3 / 3
                //
                // The default is not marginal at 192Mi — it is killed, `exit=137`, in every run,
                // at 50 and at 100 connections. This build survived 128Mi and 96Mi too. Paired A/B
                // with the order alternated put this ahead of the default on throughput and p95 in
                // 5 pairs out of 5; the absolute numbers are not quoted because the box was shared
                // with other builds and they moved by 5× between rounds.
                //
                // `-Xallocator=std` was the other candidate and is rejected on the same evidence:
                // on a service with SQLite on the request path its peak was HIGHER than this one's,
                // which is the opposite of what it did on the study service that had no database.
                binaryOption("fixedBlockPageSize", "16")

                if (staticLinux) {
                    linkerOpts("-static", "--no-dynamic-linker", "-L/usr/lib/x86_64-linux-gnu")
                    freeCompilerArgs +=
                        "-Xoverride-konan-properties=" +
                        "targetSysRoot.linux_x64=/;" +
                        "crtFilesLocation.linux_x64=usr/lib/x86_64-linux-gnu;" +
                        "libGcc.linux_x64=usr/lib/gcc/x86_64-linux-gnu/13;" +
                        "linkerGccFlags=-lgcc -lgcc_eh -lc;" +
                        "linkerKonanFlags.linux_x64=-Bstatic -lstdc++ -ldl -lm -lpthread " +
                        "--defsym __cxa_demangle=Konan_cxa_demangle --gc-sections"
                }
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
//
// `generateKoreBuildIdentity` writes into commonMain's source directories for the same reason and
// needs the same ordering. It is not the KSP task by another name: it always reruns (a commit can
// change without any input Gradle can see), so ktlint meets it on every build rather than on the
// first one.
tasks.withType<BaseKtLintCheckTask>().configureEach {
    mustRunAfter(tasks.named("kspCommonMainKotlinMetadata"))
    mustRunAfter(tasks.named("generateKoreBuildIdentity"))
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

    // The ordered shutdown, the three probes and /version. `kore-ktor` brings `kore-core` with it,
    // and both are named because both are imported here — `Main.kt` uses the lifecycle and the
    // module uses the routes.
    commonMainImplementation(libs.kore.core)
    commonMainImplementation(libs.kore.ktor)

    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(ktorLibs.server.di)
    commonMainImplementation(ktorLibs.server.auth)
    commonMainImplementation(ktorLibs.server.cio)
    commonMainImplementation(ktorLibs.server.resources)
    commonMainImplementation(ktorLibs.serialization.kotlinx.json)
    commonMainImplementation(ktorLibs.server.contentNegotiation)
    commonMainImplementation(ktorLibs.server.statusPages)
}
