package ru.workinprogress.feature.app

interface AppRepository {
    suspend fun create(
        name: String,
        type: AppType,
    ): App

    suspend fun findAll(): List<App>

    suspend fun findById(id: Int): App?

    suspend fun rename(
        id: Int,
        name: String,
    )

    /**
     * Removes the app and everything reported under it. There is no soft delete: the point of
     * deleting a test app is the disk it stops taking, and a hidden app still takes it.
     */
    suspend fun delete(id: Int)

    /** What deleting would remove, so the confirmation can say it in numbers. */
    suspend fun contents(id: Int): AppContents
}

data class AppContents(
    val groups: Int,
    val reports: Int,
)
