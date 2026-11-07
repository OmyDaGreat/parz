package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
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

fun parse(tokens: context(Sink<SyntaxDiagnostic>) () -> List<Token>) =
    collecting<SchemaSyntax, SyntaxDiagnostic> {
        Parser(tokens(), contextOf<Sink<SyntaxDiagnostic>>()).schema()
    }

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

    private fun type(): TypeExpr {
        val (text, span) = word()
        return when (text) {
            "void" -> TypeExpr.Primitive(Type.Void, span)
            "bool" -> TypeExpr.Primitive(Type.Bool, span)
            "string" -> str(span)
            "decimal" -> decimal(span)
            "option" -> TypeExpr.Option(angled { type() }, span)
            "seq" -> TypeExpr.Seq(angled { type() }, span)
            "record", "enum", "type" -> fail(InlineDecl(text), span)
            else -> TypeExpr.Named(text, span)
        }
    }

    private fun decl(): DeclSyntax {
        val keywordToken = word()
        val (name, _) = word()
        return when (keywordToken.text) {
            "record" -> DeclSyntax.Record(name, keywordToken.span, fields())
            "enum" -> DeclSyntax.Enum(name, keywordToken.span, members())
            "type" -> expect('=').let { DeclSyntax.Alias(name, keywordToken.span, type()) }
            else -> fail(ExpectedDeclKeyword(current), current.span)
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

    private fun expect(c: Char) {
        if (!accept(c)) fail(ExpectedSymbol(c, current), current.span)
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
        TypeExpr.Primitive(
            checkSyntax("string", span) {
                if (accept('(')) Type.Str(int().exactLong().also { expect(')') }) else Type.Str()
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

    private fun <T> angled(inner: () -> T): T {
        expect('<')
        return inner().also { expect('>') }
    }

    private fun decimal(span: SourceSpan): TypeExpr.Primitive {
        expect('(')
        var signum = 1
        if (accept('-')) {
            signum = -1
        }
        val precisionOrMin = int().bigInt * signum
        val precisionToken = current
        return TypeExpr.Primitive(
            checkSyntax("decimal", span) {
                if (accept(',')) {
                    val scale = int()
                    val rounding = if (accept(',')) enumWord<Rounding>() else Rounding.TOWARDS_ZERO
                    val overflow = if (accept(',')) enumWord<Overflow>() else Overflow.ERROR
                    expect(')')
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
                    expect(')')
                    Type.Decimal(precisionOrMin, max, scale.exactInt(), rounding, overflow)
                } else {
                    fail(ExpectedDecimalBounds(current), current.span)
                }
            },
            span,
        )
    }

    private fun <T> checkSyntax(
        name: String,
        span: SourceSpan,
        type: () -> T,
    ): T =
        try {
            type()
        } catch (e: IllegalArgumentException) {
            fail(InvalidType(name, e.message), span)
        }

    private fun Token.Number.exactInt(): Int =
        bigInt.let {
            try {
                it.intValue(exactRequired = true)
            } catch (_: ArithmeticException) {
                fail(IntegerOutOfRange(it, "Int"), current.span)
            }
        }

    private fun Token.Number.exactLong(): Long =
        bigInt.let {
            try {
                it.longValue(exactRequired = true)
            } catch (_: ArithmeticException) {
                fail(IntegerOutOfRange(it, "Long"), current.span)
            }
        }
}
