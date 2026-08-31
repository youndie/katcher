enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "katcher"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        // Written out by hand, and it has to be: `pluginManagement` is evaluated before any settings
        // plugin is applied — including the sborka one, which is fetched through it.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content { includeGroupByRegex("ru\\.workinprogress.*") }
        }
    }
}

// Lets Gradle fetch the JDK the toolchain asks for instead of demanding it be installed first.
// Without this, `jvmToolchain(25)` builds only on a machine where someone already put a JDK 25.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // mavenCentral() and google() with their content filters, the snapshot repository the metrik
    // agent comes from, the shared `wip` catalog, and the check that this repository's
    // `.editorconfig` is the one the rest of them use.
    //
    // The snapshot repository used to be declared in a `subprojects { repositories { } }` block in
    // the root, with a comment saying it had to be there because project repositories override the
    // settings ones. They do — which is exactly what `FAIL_ON_PROJECT_REPOS` refuses, and why the
    // block is gone: declared once here, every module resolves the agent from the same place.
    id("ru.workinprogress.sborka.settings") version "0.1.0.23"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.5.2")
        }
        create("kotlinCrypto") {
            from("org.kotlincrypto:version-catalog:0.8.0")
        }
        create("jvmLibs") {
            from(files("gradle/jvmLibs.versions.toml"))
        }
    }
}

include(":core")
include(":server")
include(":shared")
include(":client")
include(":dev:sample-kotlin-jvm")
include(":dev:sample-kotlin-native")
include(":dev:sample-android")
include(":dev:client-android")
include(":dev:android-gradle-plugin")
include(":dev:server-jvm-keycloak")
include(":dev:retrace")
