package ru.workinprogress.feature.app

import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The list is a function of the query string, so what the query string means is worth a test
 * of its own — particularly the difference between a control that was never set and one that
 * was cleared.
 */
class ErrorsQueryTest {
    private fun paginated(
        q: String? = null,
        environment: String? = null,
        release: String? = null,
        days: Int? = null,
        unresolved: Boolean = false,
    ) = AppsResource.AppId.Errors.Paginated(
        parent = AppsResource.AppId.Errors(appId = 1),
        sortBy = ErrorGroupSort.id,
        sortOrder = ErrorGroupSortOrder.desc,
        q = q,
        environment = environment,
        release = release,
        days = days,
        unresolved = unresolved,
    )

    @Test
    fun `a cleared select is not a filter for the empty string`() {
        val filter = paginated(environment = "", release = "", q = "  ").filter()

        assertNull(filter.environment)
        assertNull(filter.release)
        assertNull(filter.query)
        assertTrue(filter.isEmpty)
    }

    @Test
    fun `set controls survive the trip through the query string`() {
        val filter = paginated(q = "index", environment = "production", days = 7, unresolved = true).filter()

        assertEquals("index", filter.query)
        assertEquals("production", filter.environment)
        assertEquals(7, filter.days)
        assertTrue(filter.unresolvedOnly)
        assertEquals(4, filter.activeCount)
    }
}
