plugins {
    `kotlin-dsl`
    id("io.github.youndie.sborka.lint")
    id("io.github.youndie.sborka.publish")
}

// The group, the `maven-publish` plugin, the repository and the credentials all came from here and
// now come from `io.github.youndie.sborka.publish`. The `repositories { }` block goes with them:
// declared per project it overrides what settings declare, which is what FAIL_ON_PROJECT_REPOS
// refuses — and `google()` is in the settings list already, filtered to the groups it answers for.

// THE PLUGIN ID MOVES WITH THE GROUP, and it is not cosmetic that it does. `java-gradle-plugin`
// publishes a MARKER beside the jar whose groupId IS the plugin id, and the marker's only job is to
// point `plugins { id(...) }` at the jar. An id left on `ru.workinprogress` with the jar under
// `io.github.youndie` is a coordinate no consumer can reach: every repository in the portfolio
// declares the snapshot server in `pluginManagement` filtered to `io\.github\.youndie.*`, written
// out by hand because `pluginManagement` is evaluated before any settings plugin, and a marker under
// the old group is outside that filter.
//
// The implementation class keeps its package — that is a source move, and a different change.
gradlePlugin {
    plugins {
        register("katcherPlugin") {
            id = "io.github.youndie.katcher.gradle.plugin"
            implementationClass = "io.github.youndie.katcher.gradle.KatcherGradlePlugin"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("com.android.tools.build:gradle:9.3.1")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.10")
}
