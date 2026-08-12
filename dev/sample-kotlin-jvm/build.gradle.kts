plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation(projects.client)
    implementation(ktorLibs.client.okhttp)
    implementation(ktorLibs.client.logging)
}
