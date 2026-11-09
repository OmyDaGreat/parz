package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.ShapeError
import xyz.malefic.parz.idl.ShapeError.NonPositiveLength
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Decimal.Overflow
import xyz.malefic.parz.idl.Type.Decimal.Rounding
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedDecimalBounds
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedDeclKeyword
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedInt
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedOption
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedSymbol
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedWord
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.InlineDecl
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.IntegerOutOfRange
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.InvalidType
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.PrecisionOutOfRange

typealias SyntaxDiagnostic = Diagnostic<SyntaxErrorKind>

context(sink: Sink<SyntaxDiagnostic>)
fun parse(tokens: List<Token>) = Parser(tokens, sink).schema()

fun parseSource(source: String) = collecting { parse(lex(source)) }

@Suppress("ObjectInheritsException")
private object Abort : Exception()

internal class Parser(
    private val tokens: List<Token>,
    private val sink: Sink<SyntaxDiagnostic>,
) {
    private var pos = 0

    private val current get() = tokens[pos]
    private val here get() = current.span
    private val done get() = current is Token.EOF

    private fun fail(
        kind: SyntaxErrorKind,
        at: SourceSpan,
    ): Nothing {
        sink.report(Diagnostic(kind, at))
        throw Abort
    }

    fun schema() =
        SchemaSyntax(
            buildList {
                while (!done) {
                    val start = pos
                    try {
                        add(decl())
                    } catch (_: Abort) {
                        if (pos == start) pos++
                        skipToNext()
                    }
                }
            },
        )

    private val declKeywords = listOf("record", "enum", "type")

    private fun skipToNext() {
        while (!done) {
            val token = current
            if (token is Token.Word && token.text in declKeywords && tokens.getOrNull(pos + 1) is Token.Word) return
            pos++
        }
    }

    private fun type(): TypeSyntax {
        val (text, span) = word()
        return when (text) {
            "void" -> TypeSyntax.Primitive(Type.Void, span)
            "bool" -> TypeSyntax.Primitive(Type.Bool, span)
            "string" -> str(span)
            "decimal" -> decimal(span)
            "option" -> TypeSyntax.Option(angled { type() }.second, span)
            "seq" -> seq(span)
            "record", "enum", "type" -> fail(InlineDecl(text), span)
            else -> TypeSyntax.Named(text, span)
        }
    }

    private fun decl(): DeclSyntax {
        val (keyword, kSpan) = word()
        val (name, nSpan) = word()
        val newSpan = SourceSpan(kSpan.start, nSpan.end)
        return when (keyword) {
            "record" -> DeclSyntax.Record(name, newSpan, fields())
            "enum" -> DeclSyntax.Enum(name, newSpan, members())
            "type" -> expect('=').let { DeclSyntax.Alias(name, newSpan, type()) }
            else -> fail(ExpectedDeclKeyword(current), kSpan)
        }
    }

    private fun accept(c: Char): Boolean {
        val token = current
        if (token is Token.Symbol && token.char == c) {
            pos++
            return true
        }
        return false
    }

    @IgnorableReturnValue
    private fun expect(c: Char): Token.Symbol {
        if (!accept(c)) fail(ExpectedSymbol(c, current), current.span)
        return tokens[pos - 1] as Token.Symbol
    }

    private fun word(): Token.Word {
        val token = current as? Token.Word ?: fail(ExpectedWord(current), current.span)
        pos++
        return token
    }

    private fun int(): Token.Number {
        val token = current as? Token.Number ?: fail(ExpectedInt(current), current.span)
        pos++
        return token
    }

    private inline fun <reified T : Enum<T>> enumWord(): T {
        val (text, span) = word()
        return enumValues<T>().find { it.name.equals(text, ignoreCase = true) }
            ?: fail(ExpectedOption(enumValues<T>().map { it.name }, text), span)
    }

    private fun <T> delimited(
        open: Char,
        close: Char,
        item: (Int) -> T,
    ): List<T> {
        expect(open)
        val result =
            buildList {
                while (!accept(close)) {
                    add(item(size))
                    if (!accept(',')) {
                        expect(close)
                        break
                    }
                }
            }
        return result
    }

    private fun str(span: SourceSpan) =
        TypeSyntax.Primitive(
            if (accept('(')) {
                val n = int()
                val maxLen = n.exactLong()
                NonPositiveLength.check(maxLen).firstOrNull()?.let { fail(InvalidType(it), n.span) }
                expect(')')
                Type.Str(maxLen)
            } else {
                Type.Str()
            },
            span,
        )

    private fun fields(): List<FieldSyntax> = delimited('{', '}') { field() }

    private fun members(): List<MemberSyntax> {
        expect('{')
        var lastTag = -1
        return buildList {
            while (!accept('}')) {
                val member = member(++lastTag)
                add(member)
                lastTag = member.tag
                if (!accept(',')) {
                    expect('}')
                    break
                }
            }
        }
    }

    private fun field(): FieldSyntax {
        val (name, span) = word()
        expect(':')
        return FieldSyntax(name, span, type())
    }

    private fun member(autoTag: Int): MemberSyntax {
        val (name, span) = word()
        if (!accept('=')) return MemberSyntax(name, span, autoTag, null)
        val tagSpan = here
        return MemberSyntax(name, span, int().exactInt(), tagSpan)
    }

    private fun <T> angled(inner: () -> T): Triple<Token.Symbol, T, Token.Symbol> = Triple(expect('<'), inner(), expect('>'))

    private fun seq(keywordSpan: SourceSpan): TypeSyntax.Seq {
        val type = angled { type() }
        val newSpan = SourceSpan(keywordSpan.start, type.third.span.end)
        return TypeSyntax.Seq(type.second, newSpan)
    }

    private fun decimal(span: SourceSpan): TypeSyntax.Primitive {
        expect('(')
        var signum = 1
        if (accept('-')) {
            signum = -1
        }
        val precisionOrMin = int().bigInt * signum
        val precisionToken = current
        var endSpan: SourceSpan
        val type =
            if (accept(',')) {
                val scale = int()
                val rounding = if (accept(',')) enumWord<Rounding>() else Rounding.TOWARDS_ZERO
                val overflow = if (accept(',')) enumWord<Overflow>() else Overflow.ERROR
                endSpan = expect(')').span
                if (precisionOrMin !in BigInteger.ZERO..BigInteger(38)) fail(PrecisionOutOfRange(precisionOrMin), precisionToken.span)
                val bound = BigInteger.TEN.pow(precisionOrMin) - BigInteger.ONE
                Type.Decimal(-bound, bound, scale.exactInt(), rounding, overflow)
            } else if (accept('.') && accept('.') && accept('.')) {
                signum = if (accept('-')) -1 else 1
                val max = int().bigInt * signum
                expect(',')
                val scale = int()
                val rounding = if (accept(',')) enumWord<Rounding>() else Rounding.TOWARDS_ZERO
                val overflow = if (accept(',')) enumWord<Overflow>() else Overflow.ERROR
                endSpan = expect(')').span
                ShapeError.EmptyRange
                    .check(precisionOrMin, max)
                    .firstOrNull()
                    ?.let { fail(InvalidType(it), precisionToken.span) }
                Type.Decimal(precisionOrMin, max, scale.exactInt(), rounding, overflow)
            } else {
                fail(ExpectedDecimalBounds(current), current.span)
            }
        return TypeSyntax.Primitive(type, SourceSpan(span.start, endSpan.end))
    }

    private fun Token.Number.exactInt(): Int =
        bigInt.let {
            try {
                it.intValue(exactRequired = true)
            } catch (_: ArithmeticException) {
                fail(IntegerOutOfRange(it, "Int"), span)
            }
        }

    private fun Token.Number.exactLong(): Long =
        bigInt.let {
            try {
                it.longValue(exactRequired = true)
            } catch (_: ArithmeticException) {
                fail(IntegerOutOfRange(it, "Long"), span)
            }
        }
}
