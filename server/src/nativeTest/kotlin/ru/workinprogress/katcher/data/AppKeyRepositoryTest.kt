package ru.workinprogress.katcher.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppKeyRepository
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppKeyRepositoryImpl
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppKeyRepositoryTest : RepositoryTest() {
    private lateinit var keys: AppKeyRepository
    private lateinit var apps: AppRepository

    private var appId = 0
    private val now = 1_755_600_000_000L

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()
            apps = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            keys = AppKeyRepositoryImpl(db)
            appId = apps.create("billing", AppType.JVM).id
        }

    @Test
    fun `an issued key is accepted and carries no use yet`() =
        runTest {
            val issued = keys.issue(appId, now)

            val found = assertNotNull(keys.findActiveByKey(issued.key))
            assertEquals(appId, found.appId)
            assertNull(found.lastUsedAt)
            assertTrue(found.active)
        }

    @Test
    fun `reissuing leaves the old key working until it is revoked`() =
        runTest {
            val old = keys.issue(appId, now)
            val new = keys.issue(appId, now + 1000)

            assertNotEquals(old.key, new.key)
            assertNotNull(keys.findActiveByKey(old.key), "shipped builds still report with it")

            keys.revoke(old.id, now + 2000)

            assertNull(keys.findActiveByKey(old.key))
            assertNotNull(keys.findActiveByKey(new.key))
        }

    @Test
    fun `a revoked key keeps its row so its last use survives`() =
        runTest {
            val key = keys.issue(appId, now)
            keys.markUsed(key.id, now + 500)
            keys.revoke(key.id, now + 1000)

            val stored = keys.listByApp(appId).single()

            assertEquals(now + 500, stored.lastUsedAt)
            assertEquals(now + 1000, stored.revokedAt)
            assertTrue(!stored.active)
        }

    @Test
    fun `keys of every app come back in one call with the newest first`() =
        runTest {
            val otherApp = apps.create("shop", AppType.OTHER).id
            keys.issue(appId, now)
            val newest = keys.issue(appId, now + 1000)
            keys.issue(otherApp, now)

            val all = keys.listAll()

            assertEquals(2, all.getValue(appId).size)
            assertEquals(newest.key, all.getValue(appId).first().key)
            assertEquals(1, all.getValue(otherApp).size)
        }

    @Test
    fun `a key inherited from before keys were tracked has no creation time`() =
        runTest {
            // What migration V7 writes for the key that lived in the apps row.
            val migrated = keys.issue(appId, 0)

            assertNull(migrated.createdAtOrNull)
        }
}
