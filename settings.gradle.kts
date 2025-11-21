enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "katcher"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.3.2")
        }
        create("kotlinCrypto") {
            from("org.kotlincrypto:version-catalog:0.8.0")
        }
    }
}

include(":core")
include(":server")
include(":shared")
include(":client")
