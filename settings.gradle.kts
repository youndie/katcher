enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "katcher"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

// Lets Gradle fetch the JDK the toolchain asks for instead of demanding it be installed first.
// Without this, `jvmToolchain(25)` builds only on a machine where someone already put a JDK 25.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
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
