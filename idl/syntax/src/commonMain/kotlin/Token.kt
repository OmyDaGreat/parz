package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan

sealed interface Token {
    val span: SourceSpan

    fun pretty(): String

    data class Word(
        val text: String,
        override val span: SourceSpan,
    ) : Token {
        override fun pretty(): String = "'$text'"
    }

    data class Number(
        val text: String,
        override val span: SourceSpan,
    ) : Token {
        override fun pretty(): String = text

        val bigInt = BigInteger.parseString(text)
    }

    data class Symbol(
        val char: Char,
        override val span: SourceSpan,
    ) : Token {
        override fun pretty(): String = "'$char'"
    }

    data class EOF(
        val offset: Int,
    ) : Token {
        override val span = SourceSpan(offset, offset)

        override fun pretty(): String = "end of input"
    }
}

context(sink: Sink<SyntaxDiagnostic>)
fun lex(source: String): List<Token> =
    buildList {
        var i = 0
        while (i < source.length) {
            val c = source[i]
            val start = i
            when {
                c.isWhitespace() -> {
                    i++
                }

                c.isLetter() || c == '_' -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    add(Token.Word(source.substring(start, i), SourceSpan(start, i)))
                }

                c in '0'..'9' -> {
                    while (i < source.length && source[i] in '0'..'9') i++
                    add(Token.Number(source.substring(start, i), SourceSpan(start, i)))
                }

                c in "(){}<>,;:=-." -> {
                    add(Token.Symbol(c, SourceSpan(i, ++i)))
                }

                else -> {
                    sink.report(Diagnostic(SyntaxErrorKind.UnexpectedCharacter(c), SourceSpan(start, start + 1)))
                    i++
                }
            }
        }
        add(Token.EOF(source.trimEnd().length))
    }
