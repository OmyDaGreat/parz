package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Structural errors: the reader reports them with their location and keeps going.
 */
class ReadErrors {
    @Test
    fun strayClose() {
        val (value, diagnostics) = read(")")
        assertEquals(emptyList(), value)
        assertEquals(listOf(Diagnostic(SexpErrorKind.UnexpectedClose, SourceSpan(0, 1))), diagnostics)
    }

    @Test
    fun strayCloseBetweenItems() {
        val (value, diagnostics) = read("a ) b")
        assertEquals(listOf(atom("a"), atom("b")), value.withoutSpans())
        assertEquals(listOf(Diagnostic(SexpErrorKind.UnexpectedClose, SourceSpan(2, 3))), diagnostics)
    }

    @Test
    fun extraCloseAfterList() {
        val (value, diagnostics) = read("(a))")
        assertEquals(listOf(list(atom("a"))), value.withoutSpans())
        assertEquals(listOf(Diagnostic(SexpErrorKind.UnexpectedClose, SourceSpan(3, 4))), diagnostics)
    }

    @Test
    fun unclosedList() {
        val (value, diagnostics) = read("(a")
        assertEquals(listOf(Sexp.List(listOf(Sexp.Atom("a", SourceSpan(1, 2))), SourceSpan(0, 2))), value)
        assertEquals(listOf(Diagnostic(SexpErrorKind.UnclosedList, SourceSpan(0, 1))), diagnostics)
    }

    @Test
    fun nestedUnclosedLists() {
        val source = "(a (b"
        val (value, diagnostics) = read(source)
        assertEquals(listOf(list(atom("a"), list(atom("b")))), value.withoutSpans())
        assertEquals(
            setOf(Diagnostic(SexpErrorKind.UnclosedList, SourceSpan(0, 1)), Diagnostic(SexpErrorKind.UnclosedList, SourceSpan(3, 4))),
            diagnostics.toSet(),
        )
    }

    @Test
    fun unterminatedStringInsideList() =
        assertEquals(
            setOf(SexpErrorKind.UnterminatedString, SexpErrorKind.UnclosedList),
            read("(\"a").diagnostics.map { it.kind }.toSet(),
        )

    @Test
    fun goodInputAfterErrors() {
        val (value, diagnostics) = read(") (a \"b\\q\") (c")
        assertEquals(listOf(list(atom("a"), str("b")), list(atom("c"))), value.withoutSpans())
        assertEquals(listOf("IDL201", "IDL204", "IDL202"), diagnostics.map { it.kind.code })
    }
}
