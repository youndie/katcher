package ru.workinprogress.katcher.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.util.UUID

class KatcherGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("katcher", KatcherExtension::class.java)

        project.pluginManager.withPlugin("com.android.application") {
            val android =
                project.extensions.findByType(AppExtension::class.java)
                    ?: return@withPlugin

            android.applicationVariants.all(
                Action {
                    configureVariant(project, this, extension)
                },
            )
        }
    }

    private fun configureVariant(
        project: Project,
        variant: ApplicationVariant,
        config: KatcherExtension,
    ) {
        if (!config.enabled) return

        val buildUuid = UUID.randomUUID().toString()

        variant.buildConfigField("String", "KATCHER_BUILD_UUID", "\"$buildUuid\"")
        variant.buildConfigField("String", "KATCHER_SERVER_URL", "\"${config.serverUrl}\"")
        variant.buildConfigField("String", "KATCHER_APP_KEY", "\"${config.appKey}\"")

        if (variant.buildType.isMinifyEnabled) {
            val variantName = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val uploadTaskName = "uploadKatcherMapping$variantName"
            val mappingTaskProvider = variant.mappingFileProvider

            project.tasks.register<UploadMappingTask>(uploadTaskName) {
                this.group = "katcher"
                this.description = "Uploads ProGuard/R8 mapping file to Katcher"

                this.serverUrl.set(config.serverUrl)
                this.appKey.set(config.appKey)
                this.buildUuid.set(buildUuid)

                this.mappingFile.set(project.layout.file(mappingTaskProvider.map { it.singleFile }))

                this.dependsOn(mappingTaskProvider)
            }

            variant.assembleProvider?.configure {
                finalizedBy(uploadTaskName)
            }

            project.tasks.findByName("bundle$variantName")?.finalizedBy(uploadTaskName)
        }
    }
}

open class KatcherExtension {
    var enabled: Boolean = true
    var serverUrl: String = ""
    var appKey: String = ""
}
