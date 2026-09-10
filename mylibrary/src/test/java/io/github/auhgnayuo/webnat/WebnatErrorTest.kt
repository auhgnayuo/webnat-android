package io.github.auhgnayuo.webnat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WebnatErrorTest {
    @Test
    fun fromMapReadsCodeAndMessage() {
        val error = WebnatError.from(
            mapOf(
                "code" to 100,
                "message" to "jsapi not supported",
            ),
        )

        assertEquals(100, error?.code)
        assertEquals("jsapi not supported", error?.message)
    }

    @Test
    fun fromMapSupportsAliasFieldsAndNumericString() {
        val error = WebnatError.from(
            mapOf(
                "errCode" to "1300002",
                "errMsg" to "no such file or directory",
            ),
        )

        assertEquals(1300002, error?.code)
        assertEquals("no such file or directory", error?.message)
    }

    @Test
    fun fromMapUsesUnknownDefaultsForInvalidFields() {
        val error = WebnatError.from(
            mapOf(
                "code" to "invalid",
                "message" to null,
            ),
        )

        assertEquals(WebnatErrorCode.UNKNOWN, error?.code)
        assertEquals("Unknown Error", error?.message)
    }

    @Test
    fun fromWebnatErrorReturnsSameInstance() {
        val expected = WebnatError(7, "original")

        assertSame(expected, WebnatError.from(expected))
    }
}
