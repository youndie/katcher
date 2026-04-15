package ru.workinprogress.katcher.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.user.UserRepository
import ru.workinprogress.feature.user.data.UserRepositoryImpl
import ru.workinprogress.katcher.db.UsersCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserRepositoryTest : RepositoryTest() {
    private lateinit var repository: UserRepository

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()
            repository = UserRepositoryImpl(db, UsersCrudRepositoryImpl)
        }

    @Test
    fun `test create and findById`() =
        runTest {
            val user = repository.create("test@example.com", "Test User")
            assertEquals("test@example.com", user.email)
            assertEquals("Test User", user.name)

            val found = repository.findById(user.id)
            assertNotNull(found)
            assertEquals(user.id, found.id)
            assertEquals("test@example.com", found.email)
            assertEquals("Test User", found.name)
        }

    @Test
    fun `test findByEmail`() =
        runTest {
            repository.create("user1@example.com", "User One")
            repository.create("user2@example.com", "User Two")

            val found = repository.findByEmail("user1@example.com")
            assertNotNull(found)
            assertEquals("User One", found.name)

            val notFound = repository.findByEmail("non-existent@example.com")
            assertNull(notFound)
        }

    @Test
    fun `test findById not found`() =
        runTest {
            val found = repository.findById(999)
            assertNull(found)
        }
}
