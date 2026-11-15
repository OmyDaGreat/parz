package xyz.malefic.parz.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Span {
    @Test
    fun invalidSpans() {
        assertFailsWith<IllegalArgumentException> { SourceSpan(2, 1) }
        assertFailsWith<IllegalArgumentException> { SourceSpan(-1, 1) }
        assertFailsWith<IllegalArgumentException> { SourceSpan(-2, -1) }
    }

    @Test
    fun validSpans() {
        assertEquals(SourceSpan(0, 0), SourceSpan(0, 0))
        assertEquals(SourceSpan(3, 7), SourceSpan(3, 7))
    }

    @Test
    fun printsAsRange() = assertEquals("@3..7", SourceSpan(3, 7).toString())
}
