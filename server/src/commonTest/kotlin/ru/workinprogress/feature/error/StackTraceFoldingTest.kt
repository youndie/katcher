package ru.workinprogress.feature.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StackTraceFoldingTest {
    private val trace =
        """
        java.lang.IllegalStateException: account not found
        	at java.base/java.util.Objects.requireNonNull(Objects.java:233)
        	at io.ktor.server.routing.RoutingNode.handle(RoutingNode.kt:126)
        	at kotlinx.coroutines.BuildersKt.launch(Builders.kt:53)
        	at com.acme.billing.AccountRepository.load(AccountRepository.kt:64)
        	at com.acme.billing.AccountService.charge(AccountService.kt:88)
        	at java.base/java.lang.Thread.run(Thread.java:1583)
        """.trimIndent()

    @Test
    fun `foreign runs fold into one line that names and counts them`() {
        val chunks = StackTrace.fold(trace)

        assertIs<StackChunk.Text>(chunks[0])
        val folded = assertIs<StackChunk.Foreign>(chunks[1])
        assertEquals(3, folded.count)
        assertEquals("3 frames from java.util, io.ktor.server", folded.label)

        assertIs<StackChunk.Own>(chunks[2])
        assertIs<StackChunk.Own>(chunks[3])
        assertIs<StackChunk.Foreign>(chunks[4])
    }

    @Test
    fun `expanding shows every frame and folds nothing`() {
        val chunks = StackTrace.fold(trace, expandAll = true)

        assertTrue(chunks.none { it is StackChunk.Foreign })
        assertEquals(6, chunks.count { it is StackChunk.Own })
    }

    @Test
    fun `a single foreign frame is called a frame rather than frames`() {
        val chunks =
            StackTrace.fold(
                """
                java.lang.IllegalStateException: boom
                	at java.base/java.lang.Thread.run(Thread.java:1583)
                	at com.acme.billing.Main.main(Main.kt:12)
                """.trimIndent(),
            )

        assertEquals("1 frame from java.lang", assertIs<StackChunk.Foreign>(chunks[1]).label)
    }

    @Test
    fun `counts say how many frames there are and how many are yours`() {
        assertEquals(6 to 2, StackTrace.frameCounts(trace))
    }

    @Test
    fun `a report with no frames folds to its own text`() {
        val chunks = StackTrace.fold("Read timed out while calling the pricing service")

        assertEquals(1, chunks.size)
        assertIs<StackChunk.Text>(chunks.single())
    }
}
