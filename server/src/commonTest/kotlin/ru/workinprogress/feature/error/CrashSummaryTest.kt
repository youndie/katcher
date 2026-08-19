package ru.workinprogress.feature.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Stacktraces are written by hand here rather than captured — this repository is public. */
class CrashSummaryTest {
    @Test
    fun `type is the simple name and the message is what follows it`() {
        val summary =
            CrashSummary.of(
                """
                com.example.driver.CommandException: can't find index with key: { accountId: 1 }
                	at com.example.driver.ProtocolHelper.fail(ProtocolHelper.java:210)
                	at com.acme.billing.AccountRepository.load(AccountRepository.kt:64)
                """.trimIndent(),
            )

        assertEquals("CommandException", summary.exceptionType)
        assertEquals("can't find index with key: { accountId: 1 }", summary.message)
    }

    @Test
    fun `location is the first frame that is not somebody else's package`() {
        val summary =
            CrashSummary.of(
                """
                java.lang.IllegalStateException: account not found
                	at java.base/java.util.Objects.requireNonNull(Objects.java:233)
                	at kotlinx.coroutines.BuildersKt.launch(Builders.kt:53)
                	at io.ktor.server.routing.RoutingNode.handle(RoutingNode.kt:126)
                	at com.acme.billing.AccountService.charge(AccountService.kt:88)
                	at com.acme.billing.Main.main(Main.kt:12)
                """.trimIndent(),
            )

        assertEquals("AccountService.kt:88", summary.location)
    }

    @Test
    fun `a crash that never passed through our code says so instead of going blank`() {
        val summary =
            CrashSummary.of(
                """
                com.mongodb.MongoTimeoutException: timed out
                	at com.mongodb.internal.connection.BaseCluster.selectServer(BaseCluster.java:118)
                	at java.base/java.lang.Thread.run(Thread.java:1583)
                """.trimIndent(),
            )

        assertNull(summary.location, "no frame here belongs to an application")
        assertEquals("MongoTimeoutException", summary.exceptionType)
    }

    @Test
    fun `native frames are read too`() {
        val summary =
            CrashSummary.of(
                """
                kotlin.IllegalStateException: token expired
                	at 0   katcher   0x1042f8a10   kfun:com.acme.auth#verify(kotlin.String){}
                	at 1   katcher   0x1042f8b20   kfun:kotlin.coroutines#resume(){}
                """.trimIndent(),
            )

        assertEquals("IllegalStateException", summary.exceptionType)
        assertEquals("token expired", summary.message)
    }

    @Test
    fun `a report that is only a message keeps the message and claims no type`() {
        val summary = CrashSummary.of("Read timed out while calling the pricing service")

        assertNull(summary.exceptionType)
        assertEquals("Read timed out while calling the pricing service", summary.message)
        assertNull(summary.location)
    }

    @Test
    fun `nothing at all produces nothing rather than an empty string`() {
        val summary = CrashSummary.of(null)

        assertNull(summary.exceptionType)
        assertNull(summary.message)
        assertNull(summary.location)
    }
}
