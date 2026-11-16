package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The canonical printer, and that its output reads back to the same tree.
 */
class Printing {
    private fun assertRoundTrips(sexp: Sexp) {
        val printed = sexp.print()
        assertEquals(sexp, one(printed).withoutSpans(), "printed as: $printed")
    }

    @Test fun atom() = assertEquals("-12.34", atom("-12.34").print())

    @Test fun lists() {
        assertEquals("()", list().print())
        assertEquals("(a b (c) ())", list(atom("a"), atom("b"), list(atom("c")), list()).print())
    }

    @Test fun spansAreIgnored() = assertEquals("(a)", Sexp.List(listOf(Sexp.Atom("a", SourceSpan(5, 6))), SourceSpan(4, 7)).print())

    @Test fun plainString() = assertEquals("\"hello world\"", str("hello world").print())

    @Test fun simpleEscapes() = assertEquals("""" \" \\ \n \r \t """".replace(" ", ""), str("\"\\\n\r\t").print())

    @Test fun otherControlCharactersUseLowercaseHex() {
        assertEquals(""""\u{0}"""", str("\u0000").print())
        assertEquals(""""\u{1}"""", str("\u0001").print())
        assertEquals(""""\u{1b}"""", str("\u001b").print())
        assertEquals(""""\u{7f}"""", str("\u007f").print())
        assertEquals(""""\u{9f}"""", str("\u009f").print())
    }

    @Test fun nonAsciiIsWrittenRaw() = assertEquals("\"café 😀 中文\"", str("café 😀 中文").print())

    @Test fun readingNormalisesToCanonical() {
        assertEquals("(a (b \"x\"))", one("(  a ; comment\n\t(b   \"x\") )").print())
        assertEquals("\"A\\n\"", one(""""\u{41}\u{a}"""").print())
    }

    @Test fun roundTripTrees() {
        assertRoundTrips(list())
        assertRoundTrips(list(atom("a"), list(list(atom("b")), str("c")), list()))
        assertRoundTrips(list(atom("amount"), atom("-0.05"), list(atom("some"), str("note"))))
    }

    @Test fun roundTripEveryAsciiCharacter() = assertRoundTrips(str((0..0x7f).map { it.toChar() }.joinToString("")))

    @Test fun roundTripTrickyStrings() {
        listOf("", "\"", "\\", "\\\"", "a\"b\\c", "😀", "\u0085 ", "end\\").forEach { assertRoundTrips(str(it)) }
    }
}
