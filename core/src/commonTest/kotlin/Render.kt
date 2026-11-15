package xyz.malefic.parz.core

import kotlin.test.Test
import kotlin.test.assertEquals

class Render {
    @Test
    fun withoutSpan() = assertEquals("ERROR: boom", Diagnostic(TestKind(), null).render("anything"))

    @Test
    fun firstLine() =
        assertEquals(
            "ERROR: boom at line 1:8\nrecord A { }\n       ~",
            diagnostic(7, 8).render("record A { }"),
        )

    @Test
    fun laterLine() =
        assertEquals(
            "ERROR: boom at line 2:4\ncd ef\n   ~~",
            diagnostic(6, 8).render("ab\ncd ef\ngh"),
        )

    @Test
    fun underlinesWholeSpan() =
        assertEquals(
            "ERROR: boom at line 1:1\nrecord A\n~~~~~~",
            diagnostic(0, 6).render("record A"),
        )

    @Test
    fun emptySpanStillUnderlines() =
        assertEquals(
            "ERROR: boom at line 1:9\nrecord A\n        ~",
            diagnostic(8, 8).render("record A"),
        )

    @Test
    fun spanAtStartOfLine() =
        assertEquals(
            "ERROR: boom at line 2:1\nsecond\n~~~~~~",
            diagnostic(6, 12).render("first\nsecond"),
        )

    @Test
    fun usesMessageAndSeverity() =
        assertEquals(
            "ERROR: custom at line 1:1\nx\n~",
            diagnostic(0, 1, "custom").render("x"),
        )
}
