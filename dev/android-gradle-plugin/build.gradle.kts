plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "ru.workinprogress.katcher"

repositories {
    mavenCentral()
    google()
}

gradlePlugin {
    plugins {
        register("katcherPlugin") {
            id = "ru.workinprogress.katcher.gradle.plugin"
            implementationClass = "ru.workinprogress.katcher.gradle.KatcherGradlePlugin"
        }
    }
}

publishing {
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

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.3.0")
}
