plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.pluginSerialization) apply false

    alias(jvmLibs.plugins.jib) apply false
    alias(jvmLibs.plugins.kotlinJvm) apply false
}
