package ru.workinprogress.feature.app

/**
 * One key an application can report with. There can be more than one alive at a time — that
 * is the whole point of reissuing: the new key starts working before the shipped builds stop
 * using the old one.
 */
data class AppKey(
    val id: Long,
    val appId: Int,
    val key: String,
    val createdAt: Long,
    val lastUsedAt: Long?,
    val revokedAt: Long?,
) {
    val active: Boolean get() = revokedAt == null

    /** Keys inherited from before they were tracked have no honest creation time. */
    val createdAtOrNull: Long? get() = createdAt.takeIf { it > 0 }
}

interface AppKeyRepository {
    /** Ingest: the key an incoming report carried, if it is still accepted. */
    suspend fun findActiveByKey(key: String): AppKey?

    suspend fun listByApp(appId: Int): List<AppKey>

    /** Every app's keys in one call — the apps list draws all the cards at once. */
    suspend fun listAll(): Map<Int, List<AppKey>>

    suspend fun issue(
        appId: Int,
        at: Long,
    ): AppKey

    /** Stops the key being accepted. Kept as a row so "last used" survives the revoke. */
    suspend fun revoke(
        id: Long,
        at: Long,
    )

    /**
     * Records that a report arrived with this key. This is what makes revoking the old key a
     * decision rather than a guess.
     */
    suspend fun markUsed(
        id: Long,
        at: Long,
    )
}
