plugins {
    alias(libs.plugins.pluginSerialization)
    `maven-publish`
    kotlin("multiplatform")
    alias(libs.plugins.atomicfu)
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

kotlin {
    withSourcesJar()
    jvmToolchain(25)
    applyDefaultHierarchyTemplate()

    jvm()

    // Таргеты перечислены явно, а не выбираются по os.name: иначе в опубликованной версии
    // оказывается ровно один нативный вариант — тот, что подошёл машине сборки,
    // и приложению на iOS не с чем собираться.
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    mingwX64()

    sourceSets {
        named("nativeMain") {
            dependencies {
                implementation(libs.okio)
            }
        }
        named("nativeTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

dependencies {
    commonMainApi(projects.shared)
    commonMainImplementation(ktorLibs.client.core)
    commonMainImplementation(ktorLibs.client.contentNegotiation)
    commonMainImplementation(ktorLibs.serialization.kotlinx.json)
    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(libs.kotlinx.datetime)
}
