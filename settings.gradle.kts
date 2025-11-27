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
include(":dev:server-jvm-keycloak")
