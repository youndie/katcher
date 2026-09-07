plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
    application
}

// A SAMPLE, not a library.
kotlin {
    explicitApi = null
}

kotlin {
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation(projects.client)
    implementation(ktorLibs.client.okhttp)
    implementation(ktorLibs.client.logging)
}
