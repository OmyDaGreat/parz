package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class Strings {
    private fun value(source: String) = (one(source) as Sexp.Str).value

    private fun codes(source: String) = read(source).diagnostics.map { it.kind.code }

    @Test
    fun plain() = assertEquals(Sexp.Str("hello", SourceSpan(0, 7)), one("\"hello\""))

    @Test
    fun empty() = assertEquals(Sexp.Str("", SourceSpan(0, 2)), one("\"\""))

    @Test
    fun simpleEscapes() = assertEquals("\"\\\n\r\t", value("""" \" \\ \n \r \t """".replace(" ", "")))

    @Test
    fun unicodeEscapes() {
        assertEquals("A", value(""""\u{41}""""))
        assertEquals("é", value(""""\u{e9}""""))
        assertEquals("é", value(""""\u{E9}""""))
        assertEquals("\u0000", value(""""\u{0}""""))
    }

    @Test
    fun unicodeEscapeOutsideBasicPlane() {
        assertEquals("😀", value(""""\u{1F600}""""))
        assertEquals("😀", value(""""\u{1f600}""""))
        assertEquals(2, value(""""\u{1F600}"""").length)
        assertEquals("􏿿", value(""""\u{10FFFF}""""))
    }

    @Test
    fun rawCharactersKept() {
        assertEquals("café 😀 中文", value("\"café 😀 中文\""))
        assertEquals("line one\nline two", value("\"line one\nline two\""))
    }

    @Test
    fun escapesMixedWithText() = assertEquals("say \"hi\"\tA!", value(""""say \"hi\"\t\u{41}!""""))

    @Test
    fun badEscapeIsReportedAndSkipped() {
        val source = """"bad \q ok""""
        val (value, diagnostics) = read(source)
        assertEquals(listOf(str("bad  ok")), value.withoutSpans())
        assertEquals(listOf(SexpErrorKind.BadEscape), diagnostics.map { it.kind })
        assertEquals(source.span("""\q"""), diagnostics.single().span)
    }

    @Test
    fun badUnicodeEscapes() {
        listOf(
            """\u41""",
            """\u{}""",
            """\u{12g}""",
            """\u{1234567}""",
            """\u{110000}""",
            """\u{D800}""",
            """\u{dfff}""",
        ).forEach { escape ->
            val source = "\"$escape\""
            val diagnostics = read(source).diagnostics
            assertEquals(listOf("IDL204"), diagnostics.map { it.kind.code }, escape)
            assertEquals(1, diagnostics.single().span?.start, "$escape should be underlined from its backslash")
        }
    }

    @Test
    fun readingContinuesAfterBadEscape() = assertEquals(listOf(list(str("ab"), atom("c"))), read("""("a\qb" c)""").value.withoutSpans())

    @Test
    fun unterminated() {
        val (value, diagnostics) = read("\"abc")
        assertEquals(listOf(str("abc")), value.withoutSpans())
        assertEquals(listOf(SexpErrorKind.UnterminatedString), diagnostics.map { it.kind })
        assertEquals(SourceSpan(0, 1), diagnostics.single().span)
    }

    @Test
    fun backslashAtEndOfInput() = assertEquals(listOf("IDL203"), codes(""""x\"""))

    @Test
    fun unicodeEscapeCutOffByEndOfInput() {
        assertEquals(listOf("IDL204", "IDL203"), codes(""""\u"""))
        assertEquals(listOf("IDL204", "IDL203"), codes(""""\u{4"""))
    }
}
