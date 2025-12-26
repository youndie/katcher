plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    `maven-publish`
}

group = "ru.workinprogress.katcher"

repositories {
    google()
}

publishing {
    publications {
        withType<MavenPublication> {
            if ("android" in name) {
                artifactId = "client-android"
            }
        }
    }

    repositories {
        maven {
            name = "wip"
            url = uri("https://reposilite.kotlin.website/snapshots")
            credentials {
                username = findProperty("REPOSILITE_USER")?.toString()
                password = findProperty("REPOSILITE_SECRET")?.toString()
            }
        }
    }
}

kotlin {
    withSourcesJar()

    androidLibrary {
        namespace = "ru.workinprogress.katcher.client.android"
        compileSdk = 36
        minSdk = 24

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("consumer-rules.pro"))
        }

        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }

    sourceSets {
        androidMain {
            dependencies {
            }
        }
    }
}
