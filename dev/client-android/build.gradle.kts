plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
    id("com.android.kotlin.multiplatform.library")
}

// The group comes from `sborka.group`, and the `repositories { }` block is gone: declared per
// project it overrides what settings declare, which is what FAIL_ON_PROJECT_REPOS refuses. `google()`
// is in the settings list, filtered to androidx / com.android / com.google.

// NOT PUBLISHED ANY MORE, AND THAT IS THE POINT.
//
// This module used to publish `ru.workinprogress.katcher:client-android` (0.4.92 the last one).
// That coordinate now belongs to the android variant of the multiplatform `client`: both declared
// `object Katcher` in package `ru.workinprogress.katcher`, so an application that needed the
// multiplatform client on Android could not have them both on one classpath — it got
// "Duplicate class ru.workinprogress.katcher.Katcher" (#27).
//
// The module stays here as the single-platform Android implementation it always was — readable,
// buildable, and the thing `dev/sample-android` runs against. Consumers take the multiplatform
// client instead: `io.github.youndie.katcher:client`, which carries an `android` variant since
// 0.6.x and reads the same `KATCHER_BUILD_UUID` from BuildConfig.

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
