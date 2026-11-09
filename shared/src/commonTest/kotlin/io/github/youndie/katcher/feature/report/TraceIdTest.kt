package io.github.youndie.katcher.feature.report

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraceIdTest {
    @Test
    fun `a W3C trace id is one`() {
        assertTrue(isTraceId("4bf92f3577b34da6a3ce929d0e0e4736"))
    }

    @Test
    fun `anything that is not exactly a trace id is not one`() {
        listOf(
            null,
            "",
            "4BF92F3577B34DA6A3CE929D0E0E4736",
            "00f067aa0ba902b7",
            "00000000000000000000000000000000",
            "4bf92f3577b34da6a3ce929d0e0e4736 ",
            "4bf92f3577b34da6a3ce929d0e0e47\"><script>",
        ).forEach { value -> assertFalse(isTraceId(value), "$value") }
    }
}
