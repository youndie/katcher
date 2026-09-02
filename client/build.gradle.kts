plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.multiplatform")
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
    id("ru.workinprogress.sborka.publish")
    id("org.jetbrains.kotlinx.atomicfu")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    withSourcesJar()
    applyDefaultHierarchyTemplate()

    jvm()

    android {
        namespace = "ru.workinprogress.katcher.client"
        compileSdk = 36
        minSdk = 24

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("consumer-rules.pro"))
        }
    }

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
        // JVM и Android делят всё, кроме каталога отчётов: обработчик исключений тот же
        // (Thread.setDefaultUncaughtExceptionHandler Android чтит), хранилище то же файловое.
        val jvmSharedMain = create("jvmSharedMain") { dependsOn(commonMain.get()) }
        named("jvmMain") { dependsOn(jvmSharedMain) }
        named("androidMain") { dependsOn(jvmSharedMain) }

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
        named("jvmTest") {
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
