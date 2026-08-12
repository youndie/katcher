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
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("com.android.tools.build:gradle:9.3.1")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.10")
}
