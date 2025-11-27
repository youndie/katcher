plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation(projects.client)
    implementation(ktorLibs.client.okhttp)
    implementation(ktorLibs.client.logging)
}
