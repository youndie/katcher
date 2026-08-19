package ru.workinprogress.feature.error

import ru.workinprogress.feature.error.ProcessReportUseCase.Companion.generateFingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Stacktraces here are written by hand rather than copied from a live app: a fixture taken
 * from a reporting service names its internals, and this repository is public.
 */
class FingerprintTest {
    private val driverFrames =
        """
        |	at com.example.driver.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:210)
        |	at com.example.driver.InternalStreamConnection.lambda${'$'}sendCommandMessageAsync${'$'}19(InternalStreamConnection.java:740)
        |	at com.example.driver.InternalStreamConnection${'$'}MessageCallback.onResult(InternalStreamConnection.java:1059)
        |	at com.example.async.SingleResultCallback.complete(SingleResultCallback.java:69)
        |	at java.base/java.lang.Thread.run(Thread.java:1583)
        """.trimMargin()

    /** A driver that prints the whole server response into the message it throws. */
    private fun commandFailure(
        clusterTime: Long,
        signature: String,
        host: String = "db-0.db-headless.ns.svc.cluster.local:27017",
    ) = "com.example.driver.CommandException: Command execution failed with error 27 (IndexNotFound): " +
        "'can't find index with key: { accountId: 1 }' on server $host. The full response is " +
        """{"ok": 0.0, "code": 27, "codeName": "IndexNotFound", """ +
        """"${'$'}clusterTime": {"clusterTime": {"${'$'}timestamp": {"t": $clusterTime, "i": 1}}, """ +
        """"signature": {"hash": {"${'$'}binary": {"base64": "$signature", "subType": "00"}}, """ +
        """"keyId": 7398765432109876543}}, """ +
        """"operationTime": {"${'$'}timestamp": {"t": $clusterTime, "i": 1}}}""" +
        "\n" + driverFrames

    @Test
    fun `a response dump in the message does not split the group`() {
        val first = commandFailure(clusterTime = 1755594480, signature = "K1n2Zq0/abCdEf+ghIjKlMnO")
        val second = commandFailure(clusterTime = 1755598080, signature = "9xQ7Yy1/zzAaBb+ccDdEeFfG")

        assertEquals(generateFingerprint(first), generateFingerprint(second))
    }

    @Test
    fun `the replica that answered does not split the group`() {
        val first =
            commandFailure(
                clusterTime = 1755594480,
                signature = "K1n2Zq0/abCdEf+ghIjKlMnO",
                host = "db-0.db-headless.ns.svc.cluster.local:27017",
            )
        val second =
            commandFailure(
                clusterTime = 1755594480,
                signature = "K1n2Zq0/abCdEf+ghIjKlMnO",
                host = "db-1.db-headless.ns.svc.cluster.local:27017",
            )

        assertEquals(generateFingerprint(first), generateFingerprint(second))
    }

    @Test
    fun `a connection id in the message does not split the group`() {
        fun poolCleared(clusterId: String) =
            "com.example.driver.ConnectionPoolClearedException: Connection pool for " +
                "ServerId{clusterId=ClusterId{value='$clusterId', description='payments'}, " +
                "address=db-0.db-headless.ns.svc.cluster.local:27017} was cleared\n$driverFrames"

        assertEquals(
            generateFingerprint(poolCleared("6a7dbb6e6de3d1065a7ca0ed")),
            generateFingerprint(poolCleared("6a7dbc247982f0c2db488223")),
        )
    }

    @Test
    fun `a different failure through the same driver stays a separate group`() {
        val indexMissing = commandFailure(clusterTime = 1755594480, signature = "K1n2Zq0/abCdEf+ghIjKlMnO")
        val duplicateKey =
            "com.example.driver.CommandException: Command execution failed with error 11000 (DuplicateKey): " +
                "'E11000 duplicate key error' on server db-0.db-headless.ns.svc.cluster.local:27017\n$driverFrames"

        assertNotEquals(generateFingerprint(indexMissing), generateFingerprint(duplicateKey))
    }

    @Test
    fun `a different exception type stays a separate group`() {
        val nullPointer = "java.lang.NullPointerException\n$driverFrames"
        val illegalState = "java.lang.IllegalStateException\n$driverFrames"

        assertNotEquals(generateFingerprint(nullPointer), generateFingerprint(illegalState))
    }

    @Test
    fun `a different throw site stays a separate group`() {
        val fromRepository =
            """
            |java.lang.IllegalStateException: account not found
            |	at com.example.app.AccountRepository.load(AccountRepository.kt:41)
            |	at com.example.app.AccountService.charge(AccountService.kt:88)
            """.trimMargin()
        val fromImporter =
            """
            |java.lang.IllegalStateException: account not found
            |	at com.example.app.ImportJob.resolve(ImportJob.kt:17)
            |	at com.example.app.ImportJob.run(ImportJob.kt:9)
            """.trimMargin()

        assertNotEquals(generateFingerprint(fromRepository), generateFingerprint(fromImporter))
    }

    @Test
    fun `an edit above the throw site keeps the group`() {
        fun trace(line: Int) =
            """
            |java.lang.IllegalStateException: account not found
            |	at com.example.app.AccountRepository.load(AccountRepository.kt:$line)
            |	at com.example.app.AccountService.charge(AccountService.kt:88)
            """.trimMargin()

        assertEquals(generateFingerprint(trace(41)), generateFingerprint(trace(58)))
    }

    @Test
    fun `native frames group by symbol`() {
        fun trace(address: String) =
            """
            |kotlin.IllegalStateException: token expired
            |	at 0   katcher   $address   kfun:ru.workinprogress.katcher.auth#verify(kotlin.String){}
            |	at 1   katcher   $address   kfun:ru.workinprogress.katcher.auth#route(){}
            """.trimMargin()

        assertEquals(generateFingerprint(trace("0x1042f8a10")), generateFingerprint(trace("0x1058c3d44")))
    }

    @Test
    fun `a report without frames still groups by its message`() {
        fun timedOut(millis: Int) = "Read timed out after $millis ms while calling the pricing service"

        assertEquals(generateFingerprint(timedOut(2000)), generateFingerprint(timedOut(2137)))
        assertNotEquals(
            generateFingerprint(timedOut(2000)),
            generateFingerprint("Read timed out after 2000 ms while calling the billing service"),
        )
    }
}
