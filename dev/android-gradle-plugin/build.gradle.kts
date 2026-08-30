plugins {
    `kotlin-dsl`
    id("ru.workinprogress.sborka.lint")
    id("ru.workinprogress.sborka.publish")
}

// The group, the `maven-publish` plugin, the repository and the credentials all came from here and
// now come from `ru.workinprogress.sborka.publish`. The `repositories { }` block goes with them:
// declared per project it overrides what settings declare, which is what FAIL_ON_PROJECT_REPOS
// refuses — and `google()` is in the settings list already, filtered to the groups it answers for.

gradlePlugin {
    plugins {
        register("katcherPlugin") {
            id = "ru.workinprogress.katcher.gradle.plugin"
            implementationClass = "ru.workinprogress.katcher.gradle.KatcherGradlePlugin"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("com.android.tools.build:gradle:9.3.1")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.10")
}
