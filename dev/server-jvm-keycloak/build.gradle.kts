import io.ktor.plugin.features.DockerImageRegistry

plugins {
    alias(jvmLibs.plugins.jib)
    alias(jvmLibs.plugins.kotlinJvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.pluginSerialization)

    application
}

version = "0.1.0"

application {
    mainClass.set("ru.workinprogress.katcher.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.shared)

    implementation(jvmLibs.logback)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.resources)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.statusPages)

    implementation(jvmLibs.slf4j.api)

    implementation(libs.kotlinx.datetime)

    implementation(project.dependencies.platform(jvmLibs.koin.bom))
    implementation(jvmLibs.koin.ktor)
    implementation(jvmLibs.koin.logger.slf4j)

    implementation(libs.kotlinx.serialization.json)
    implementation(ktorLibs.serialization.kotlinx.json)

    implementation(jvmLibs.exposed.core)
    implementation(jvmLibs.exposed.dao)
    implementation(jvmLibs.exposed.jdbc)
    implementation(jvmLibs.exposed.migrations)

    implementation(jvmLibs.sqlite.jdbc)

    implementation(ktorLibs.server.htmx)
    implementation(ktorLibs.htmx)
    implementation(ktorLibs.htmx.html)
    implementation(ktorLibs.server.htmlBuilder)

    implementation(ktorLibs.client.okhttp)
    implementation(ktorLibs.client.resources)
    implementation(ktorLibs.client.contentNegotiation)

    testImplementation(jvmLibs.kotlin.test.junit)
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("katcher-backend")
        imageTag.set(providers.gradleProperty("VERSION").getOrElse("snapshot"))
        customBaseImage.set("gcr.io/distroless/java21-debian12")
        externalRegistry.set(
            DockerImageRegistry.externalRegistry(
                username = providers.gradleProperty("REGISTRY_USERNAME"),
                password = providers.gradleProperty("REGISTRY_PASSWORD"),
                project = provider { "katcher-backend" },
                hostname = providers.gradleProperty("REGISTRY_HOSTNAME"),
            ),
        )
    }
}
