package ru.workinprogress.katcher.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.register
import java.util.UUID

abstract class KatcherExtension {
    abstract val enabled: Property<Boolean>
    abstract val serverUrl: Property<String>
    abstract val appKey: Property<String>

    init {
        enabled.convention(true)
        serverUrl.convention("")
        appKey.convention("")
    }
}

class KatcherGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("katcher", KatcherExtension::class.java)

        project.pluginManager.withPlugin("com.android.application") {
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

            androidComponents.onVariants { variant ->
                if (!extension.enabled.get()) return@onVariants

                val buildUuid = UUID.randomUUID().toString()

                variant.buildConfigFields?.put(
                    "KATCHER_BUILD_UUID",
                    BuildConfigField("String", "\"$buildUuid\"", "Katcher Build UUID"),
                )
                variant.buildConfigFields?.put(
                    "KATCHER_SERVER_URL",
                    BuildConfigField("String", "\"${extension.serverUrl.get()}\"", "Katcher Server URL"),
                )
                variant.buildConfigFields?.put(
                    "KATCHER_APP_KEY",
                    BuildConfigField("String", "\"${extension.appKey.get()}\"", "Katcher App Key"),
                )

                val variantName =
                    variant.name.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    }

                val uploadTaskProvider =
                    project.tasks.register<UploadMappingTask>("uploadKatcherMapping$variantName") {
                        group = "katcher"
                        description = "Uploads ProGuard/R8 mapping file to Katcher for $variantName"

                        serverUrl.set(extension.serverUrl)
                        appKey.set(extension.appKey)
                        this.buildUuid.set(buildUuid)
                        mappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
                    }
                project.afterEvaluate {
                    try {
                        project.tasks.named("assemble$variantName").configure {
                            finalizedBy(uploadTaskProvider)
                        }
                    } catch (e: Exception) {
                        project.logger.info("Task assemble$variantName not found, skipping finalizedBy")
                    }

                    try {
                        project.tasks.named("bundle$variantName").configure {
                            finalizedBy(uploadTaskProvider)
                        }
                    } catch (e: Exception) {
                        project.logger.info("Task bundle$variantName not found, skipping finalizedBy")
                    }
                }
            }
        }
    }
}
