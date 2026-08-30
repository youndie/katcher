plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

// The group comes from `sborka.group`, and the `repositories { }` block is gone: declared per
// project it overrides what settings declare, which is what FAIL_ON_PROJECT_REPOS refuses. `google()`
// is in the settings list, filtered to androidx / com.android / com.google.

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

    android {
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
