package ru.workinprogress.katcher.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppRepositoryTest : RepositoryTest() {
    private lateinit var repository: AppRepository

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()
            repository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
        }

    @Test
    fun `test create and findById`() =
        runTest {
            val name = "Test App"
            val type = AppType.ANDROID
            val created = repository.create(name, type)

            assertEquals(name, created.name)
            assertEquals(type, created.type)
            assertNotNull(created.apiKey)

            val found = repository.findById(created.id)
            assertNotNull(found)
            assertEquals(created.id, found.id)
            assertEquals(name, found.name)
            assertEquals(type, found.type)
            assertEquals(created.apiKey, found.apiKey)
        }

    @Test
    fun `test findByApiKey`() =
        runTest {
            val name = "API Key Test"
            val type = AppType.ANDROID
            val created = repository.create(name, type)

            val found = repository.findByApiKey(created.apiKey)
            assertNotNull(found)
            assertEquals(created.id, found.id)
            assertEquals(created.apiKey, found.apiKey)

            val notFound = repository.findByApiKey("non-existent-key")
            assertNull(notFound)
        }

    @Test
    fun `test findAll`() =
        runTest {
            repository.create("App 1", AppType.ANDROID)
            repository.create("App 2", AppType.COMPOSE_MULTIPLATFORM)
            repository.create("App 3", AppType.OTHER)

            val all = repository.findAll()
            assertEquals(3, all.size)

            val names = all.map { it.name }.toSet()
            assertEquals(setOf("App 1", "App 2", "App 3"), names)
        }
}
