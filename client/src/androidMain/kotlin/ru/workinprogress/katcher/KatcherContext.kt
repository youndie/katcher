package ru.workinprogress.katcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

internal object KatcherContext {
    @Volatile
    internal var applicationContext: Context? = null

    internal fun install(context: Context) {
        applicationContext = context.applicationContext ?: context
    }
}

/**
 * Отдаёт библиотеке контекст приложения без единой строчки у потребителя: провайдер, объявленный в
 * манифесте aar, создаётся системой до `Application.onCreate`, то есть раньше любого места, где
 * приложение могло бы позвать `Katcher.start { }`.
 */
public class KatcherInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let(KatcherContext::install)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

/**
 * Запасной путь на случай, когда провайдер в манифест приложения не попал (его удалили слиянием
 * манифестов или библиотека подключена как jar). Звать до [Katcher.start].
 */
public fun Katcher.installContext(context: Context) {
    KatcherContext.install(context)
}
