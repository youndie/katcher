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
        create<MavenPublication>("mavenJava") {
            from(components["kotlin"])
            artifact(tasks.named("sourcesJar"))
            groupId = group.toString()
            artifactId = "katcher-android-client"
            version = project.version.toString()
        }
    }
}

kotlin {
    withSourcesJar()

    androidLibrary {
        namespace = "ru.workinprogress.katcher.client.android"
        compileSdk = 36
        minSdk = 24

        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }

    sourceSets {
        androidMain {
            dependencies {
            }
        }
    }
}
