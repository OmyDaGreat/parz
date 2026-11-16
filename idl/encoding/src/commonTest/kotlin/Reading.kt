package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class Reading {
    @Test
    fun emptyInput() {
        assertEquals(emptyList(), readOk(""))
        assertEquals(emptyList(), readOk(" \n\t\r "))
        assertEquals(emptyList(), readOk("; only a comment"))
    }

    @Test
    fun atom() = assertEquals(Sexp.Atom("pending", SourceSpan(0, 7)), one("pending"))

    @Test
    fun decimalAtoms() {
        listOf("12.34", "-0.05", "0", "-120", "999999999999999999999999999999.99").forEach {
            assertEquals(atom(it), one(it).withoutSpans())
        }
    }

    @Test
    fun atomsTakeAnyNonDelimiter() = assertEquals(atom("a.b-c:d/e+f@g"), one("a.b-c:d/e+f@g").withoutSpans())

    @Test
    fun severalTopLevel() = assertEquals(listOf(atom("a"), atom("b"), list(atom("c"))), readOk("a b (c)").withoutSpans())

    @Test
    fun emptyLists() {
        assertEquals(Sexp.List(emptyList(), SourceSpan(0, 2)), one("()"))
        assertEquals(Sexp.List(emptyList(), SourceSpan(0, 3)), one("( )"))
    }

    @Test
    fun nestedSpans() {
        val source = "(a (b c) \"d\")"
        assertEquals(
            Sexp.List(
                listOf(
                    Sexp.Atom("a", source.span("a")),
                    Sexp.List(
                        listOf(Sexp.Atom("b", source.span("b")), Sexp.Atom("c", source.span("c"))),
                        source.span("(b c)"),
                    ),
                    Sexp.Str("d", source.span("\"d\"")),
                ),
                SourceSpan(0, source.length),
            ),
            one(source),
        )
    }

    @Test
    fun spansAfterLeadingWhitespace() {
        val source = "  \n (x)"
        assertEquals(source.span("(x)"), one(source).span)
    }

    @Test
    fun delimitersEndAtoms() {
        assertEquals(listOf(list(atom("a")), list(atom("b"))), readOk("(a)(b)").withoutSpans())
        assertEquals(listOf(atom("a"), str("b")), readOk("a\"b\"").withoutSpans())
        assertEquals(listOf(atom("a")), readOk("a;b").withoutSpans())
    }

    @Test
    fun anyWhitespaceSeparates() = assertEquals(list(atom("a"), atom("b"), atom("c"), atom("d")), one("(a\tb\nc\r\nd)").withoutSpans())

    @Test
    fun comments() {
        assertEquals(list(atom("a"), atom("b")), one("(a ; a note (with parens) and \"quotes\n b)").withoutSpans())
        assertEquals(listOf(atom("a")), readOk("a ; to the end without a newline").withoutSpans())
        assertEquals(listOf(atom("a"), atom("b")), readOk("; first\na\n; second\nb").withoutSpans())
    }

    @Test
    fun semicolonInsideStringIsNotAComment() = assertEquals(str("a;b"), one("\"a;b\"").withoutSpans())

    @Test
    fun deepNesting() {
        val depth = 200
        val source = "(".repeat(depth) + "x" + ")".repeat(depth)
        var expected: Sexp = atom("x")
        repeat(depth) { expected = list(expected) }
        assertEquals(expected, one(source).withoutSpans())
    }
}
