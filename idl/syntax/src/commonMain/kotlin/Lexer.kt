package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan

sealed interface Token {
    val offset: Int
    val span: SourceSpan

    fun pretty(): String

    data class Word(
        val text: String,
        override val offset: Int,
    ) : Token {
        override val span = SourceSpan(offset, offset + text.length)

        override fun pretty(): String = "'$text'"
    }

    data class Number(
        val text: String,
        override val offset: Int,
    ) : Token {
        override val span = SourceSpan(offset, offset + text.length)

        override fun pretty(): String = text

        val bigInt = BigInteger.parseString(text)
    }

    data class Symbol(
        val char: Char,
        override val offset: Int,
    ) : Token {
        override val span = SourceSpan(offset, offset + 1)

        override fun pretty(): String = "'$char'"
    }

    data class EOF(
        override val offset: Int,
    ) : Token {
        override val span = SourceSpan(offset, offset)

        override fun pretty(): String = "end of input"
    }
}

sealed interface SyntaxErrorKind : DiagnosticKind {
    data class UnexpectedCharacter(
        val char: Char,
    ) : SyntaxErrorKind {
        override val code = "IDL001"
        override val message = "Unexpected character $char"
    }

    data class InlineDecl(
        val keyword: String,
    ) : SyntaxErrorKind {
        override val code = "IDL002"
        override val message = "Inline $keyword declarations are not allowed"
    }

    data class ExpectedDeclKeyword(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL003"
        override val message = "Expected 'record', 'enum', or 'type', but instead found ${found.pretty()}"
    }

    data class ExpectedSymbol(
        val expected: Char,
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL004"
        override val message = "Expected '$expected', but instead found ${found.pretty()}"
    }

    data class ExpectedWord(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL005"
        override val message = "Expected a word, but instead found ${found.pretty()}"
    }

    data class ExpectedInt(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL006"
        override val message = "Expected an integer, but instead found ${found.pretty()}"
    }

    data class ExpectedOption(
        val options: List<String>,
        val found: String,
    ) : SyntaxErrorKind {
        override val code = "IDL007"
        override val message = "Expected one of ${options.joinToString { "'$it'" }}, but instead found '$found'"
    }

    data class PrecisionOutOfRange(
        val value: BigInteger,
    ) : SyntaxErrorKind {
        override val code = "IDL008"
        override val message = "Precision must be between 0 and 38"
    }

    data class ExpectedDecimalBounds(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL009"
        override val message = "Expected ',' or '...' in the decimal type, but instead found ${found.pretty()}"
    }

    data class InvalidType(
        val typeName: String,
        val reason: String?,
    ) : SyntaxErrorKind {
        override val code = "IDL010"
        override val message = "Invalid $typeName${if (reason != null) ": $reason" else ""}"
    }

    data class IntegerOutOfRange(
        val value: BigInteger,
        val target: String,
    ) : SyntaxErrorKind {
        override val code = "IDL011"
        override val message = "Integer value $value is out of range for $target"
    }
}

context(sink: Sink<Diagnostic<SyntaxErrorKind>>)
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
                    add(Token.Word(source.substring(start, i), start))
                }

                c in '0'..'9' -> {
                    while (i < source.length && source[i].isDigit()) i++
                    add(Token.Number(source.substring(start, i), start))
                }

                c in "(){}<>,;:=-." -> {
                    add(Token.Symbol(c, i++))
                }

                else -> {
                    sink.report(Diagnostic(SyntaxErrorKind.UnexpectedCharacter(c), SourceSpan(start, start + 1)))
                    i++
                }
            }
        }
        add(Token.EOF(source.trimEnd().length))
    }
