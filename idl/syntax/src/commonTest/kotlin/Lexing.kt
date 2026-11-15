package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.UnexpectedCharacter
import kotlin.test.Test
import kotlin.test.assertEquals

class Lexing {
    private fun tokens(source: String) = collecting { lex(source) }

    @Test
    fun wordsNumbersAndSymbols() {
        val result = tokens("record A_1 { x: 12 }")
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                Token.Word("record", SourceSpan(0, 6)),
                Token.Word("A_1", SourceSpan(7, 10)),
                Token.Symbol('{', SourceSpan(11, 12)),
                Token.Word("x", SourceSpan(13, 14)),
                Token.Symbol(':', SourceSpan(14, 15)),
                Token.Number("12", SourceSpan(16, 18)),
                Token.Symbol('}', SourceSpan(19, 20)),
                Token.EOF(20),
            ),
            result.value,
        )
    }

    @Test
    fun everySymbol() {
        val symbols = "(){}<>,;:=-."
        assertEquals(symbols.toList(), tokens(symbols).value.filterIsInstance<Token.Symbol>().map { it.char })
    }

    @Test
    fun wordsMayStartWithUnderscoreOrUseUnicodeLetters() {
        assertEquals(listOf("_a", "café"), tokens("_a café").value.filterIsInstance<Token.Word>().map { it.text })
    }

    @Test
    fun numberStopsAtLetter() {
        assertEquals(
            listOf(Token.Number("12", SourceSpan(0, 2)), Token.Word("ab", SourceSpan(2, 4)), Token.EOF(4)),
            tokens("12ab").value,
        )
    }

    @Test
    fun eofIgnoresTrailingWhitespace() {
        assertEquals(Token.EOF(1), tokens("a  \n\t").value.last())
        assertEquals(listOf(Token.EOF(0)), tokens("").value)
    }

    @Test
    fun unexpectedCharacterIsReportedAndSkipped() {
        val result = tokens("a @ b")
        assertEquals(listOf(Diagnostic(UnexpectedCharacter('@'), SourceSpan(2, 3))), result.diagnostics)
        assertEquals(listOf("a", "b"), result.value.filterIsInstance<Token.Word>().map { it.text })
    }

    @Test
    fun nonAsciiDigitAfterNumber() {
        val result = tokens("1٣")
        assertEquals(listOf(Diagnostic(UnexpectedCharacter('٣'), SourceSpan(1, 2))), result.diagnostics)
    }

    @Test
    fun prettyTokens() {
        assertEquals("'record'", Token.Word("record", SourceSpan(0, 6)).pretty())
        assertEquals("12", Token.Number("12", SourceSpan(0, 2)).pretty())
        assertEquals("'{'", Token.Symbol('{', SourceSpan(0, 1)).pretty())
        assertEquals("end of input", Token.EOF(0).pretty())
    }
}
