package ru.workinprogress.feature.app

/**
 * The numbers behind one card in the apps list.
 *
 * [dailyCrashes] is oldest-first and its last bucket is the running 24 hours, so the bars
 * read left to right and the head of the list is what "crashes / 24h" counts.
 */
data class AppOverview(
    val appId: Int,
    val unseenGroups: Int,
    val dailyCrashes: List<Int>,
    val lastCrashAt: Long?,
    val newGroupsToday: Int,
    val fixesWaiting: Int,
) {
    val crashes24h: Int get() = dailyCrashes.lastOrNull() ?: 0

    /** No report has ever arrived — a different thing from "nothing arrived lately". */
    val neverReported: Boolean get() = lastCrashAt == null

    companion object {
        const val DAYS = 7

        fun silent(appId: Int) =
            AppOverview(
                appId = appId,
                unseenGroups = 0,
                dailyCrashes = List(DAYS) { 0 },
                lastCrashAt = null,
                newGroupsToday = 0,
                fixesWaiting = 0,
            )
    }
}

interface AppOverviewRepository {
    /**
     * Overviews for every app, keyed by app id. [now] is passed in rather than read inside:
     * the day buckets are cut relative to it, and a test that cannot choose "now" cannot
     * check them.
     */
    suspend fun overview(
        userId: Int,
        now: Long,
    ): Map<Int, AppOverview>
}
