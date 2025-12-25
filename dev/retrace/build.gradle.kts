plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    val nativeTarget =
        when {
            hostOs == "Mac OS X" && arch == "x86_64" -> macosX64("native")
            hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("native")
            hostOs == "Linux" && (arch == "x86_64" || arch == "amd64") -> linuxX64("native")
            hostOs == "Linux" && arch == "aarch64" -> linuxArm64("native")
            hostOs.startsWith("Windows") -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

    nativeTarget.apply {
    }
}

dependencies {
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.okio)
}
