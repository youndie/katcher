package ru.workinprogress.katcher

import android.os.Build

public fun getSystemAttributes(): Map<String, String> {
    val attributes =
        mutableMapOf(
            "device.os" to "Android ${Build.VERSION.RELEASE}",
            "device.arch" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            "device.model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "device.brand" to Build.BRAND,
            "runtime.name" to "Android",
            "runtime.version" to Build.VERSION.SDK_INT.toString(),
            "app.thread" to Thread.currentThread().name,
        )

    // Тот же ключ, что кладёт client-android, и тот, который сервер ищет в context у отчёта
    // (ProcessReportUseCase): без него загруженный R8-маппинг не с чем сопоставить.
    buildUuid()?.let { attributes["build_uuid"] = it }

    return attributes
}

private fun buildUuid(): String? {
    val context = KatcherContext.applicationContext ?: return null
    return try {
        Class
            .forName("${context.packageName}.BuildConfig")
            .getField("KATCHER_BUILD_UUID")
            .get(null) as? String
    } catch (e: Exception) {
        null
    }
}
