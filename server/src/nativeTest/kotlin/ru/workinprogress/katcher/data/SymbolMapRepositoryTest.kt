package ru.workinprogress.katcher.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.app.AppRepository
import ru.workinprogress.feature.app.AppType
import ru.workinprogress.feature.app.data.AppRepositoryImpl
import ru.workinprogress.feature.symbolication.MappingType
import ru.workinprogress.feature.symbolication.SymbolMap
import ru.workinprogress.feature.symbolication.SymbolMapRepository
import ru.workinprogress.feature.symbolication.data.SymbolMapRepositoryImpl
import ru.workinprogress.katcher.db.AppsCrudRepositoryImpl
import ru.workinprogress.katcher.db.SymbolMapCrudRepositoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SymbolMapRepositoryTest : RepositoryTest() {
    private lateinit var repository: SymbolMapRepository
    private lateinit var appRepository: AppRepository

    private var appId = 0

    @BeforeTest
    fun setup() =
        runTest {
            setupSchema()

            appRepository = AppRepositoryImpl(db, AppsCrudRepositoryImpl)
            repository = SymbolMapRepositoryImpl(db, SymbolMapCrudRepositoryImpl)

            val app = appRepository.create("test-app", AppType.ANDROID)
            appId = app.id
        }

    @Test
    fun `test save and find`() =
        runTest {
            val symbolMap =
                SymbolMap(
                    id = 0,
                    appId = appId,
                    buildUuid = "test-uuid",
                    type = MappingType.ANDROID_PROGUARD,
                    filePath = "/path/to/mapping.txt",
                    versionName = "1.0.0",
                    createdAt = 123456789L,
                )

            val id = repository.save(symbolMap)
            assertEquals(1L, id)

            val found = repository.find(appId, "test-uuid")
            assertNotNull(found)
            assertEquals(appId, found.appId)
            assertEquals("test-uuid", found.buildUuid)
            assertEquals(MappingType.ANDROID_PROGUARD, found.type)
            assertEquals("/path/to/mapping.txt", found.filePath)
            assertEquals("1.0.0", found.versionName)
            assertEquals(123456789L, found.createdAt)
        }

    @Test
    fun `test find not found`() =
        runTest {
            val found = repository.find(appId, "non-existent")
            assertNull(found)
        }
}
