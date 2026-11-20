package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.ShapeErrorKind
import xyz.malefic.parz.idl.ShapeErrorKind.NonPositiveLength
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
    private val prior get() = tokens[pos - 1]
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

    private val declKeywords = listOf("record", "variant", "enum", "type")

    private val integerShorthandMap =
        mapOf(
            "u8" to (BigInteger.ZERO to BigInteger(2).pow(8).dec()),
            "u16" to (BigInteger.ZERO to BigInteger(2).pow(16).dec()),
            "u32" to (BigInteger.ZERO to BigInteger(2).pow(32).dec()),
            "u64" to (BigInteger.ZERO to BigInteger(2).pow(64).dec()),
            "i8" to (BigInteger(2).pow(7).negate() to BigInteger(2).pow(7).dec()),
            "i16" to (BigInteger(2).pow(15).negate() to BigInteger(2).pow(15).dec()),
            "i32" to (BigInteger(2).pow(31).negate() to BigInteger(2).pow(31).dec()),
            "i64" to (BigInteger(2).pow(63).negate() to BigInteger(2).pow(63).dec()),
        )

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
            in integerShorthandMap.keys -> integerShorthandMap[text]!!.let { decimalShorthand(span, it.first, it.second) }
            "option" -> angled { type() }.let { TypeSyntax.Option(it.second, SourceSpan(span.start, it.third.span.end)) }
            "seq" -> seq(span)
            "map" -> map(span)
            in declKeywords -> fail(InlineDecl(text), span)
            else -> TypeSyntax.Named(text, span)
        }
    }

    private fun decl(): DeclSyntax {
        val keywordToken = word()
        val (keyword, kSpan) = keywordToken
        val (name, nSpan) = word()
        val newSpan = SourceSpan(kSpan.start, nSpan.end)
        return when (keyword) {
            "record" -> DeclSyntax.Record(name, newSpan, fields('{' to '}'))
            "variant" -> DeclSyntax.Variant(name, newSpan, members())
            "enum" -> DeclSyntax.Variant(name, newSpan, members(), true)
            "type" -> expect('=').let { DeclSyntax.Alias(name, newSpan, type()) }
            else -> fail(ExpectedDeclKeyword(keywordToken), kSpan)
        }
    }

    private fun peek(c: Char): Boolean = current.let { token -> token is Token.Symbol && token.char == c }

    private fun accept(c: Char): Boolean = peek(c).also { if (it) pos++ }

    @IgnorableReturnValue
    private fun expect(c: Char): Token.Symbol {
        if (!accept(c)) fail(ExpectedSymbol(c, current), current.span)
        return prior as Token.Symbol
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

    private inline fun <reified T : Enum<T>> tryEnumWord(): T? {
        val token = current as? Token.Word ?: return null
        return enumValues<T>().find { it.name.equals(token.text, ignoreCase = true) }?.also { pos++ }
    }

    private fun <T> delimited(
        open: Char,
        close: Char,
        item: (Int) -> T,
    ): Triple<Token.Symbol, List<T>, Token.Symbol> {
        val startToken = expect(open)
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
        return Triple(startToken, result, prior as Token.Symbol)
    }

    private fun str(span: SourceSpan): TypeSyntax.Primitive {
        var endSpan = span.end
        return TypeSyntax.Primitive(
            if (accept('(')) {
                val n = int()
                val maxLen = n.exactLong()
                NonPositiveLength.check(maxLen).firstOrNull()?.let { fail(InvalidType(it), n.span) }
                endSpan = expect(')').span.end
                Type.Str(maxLen)
            } else {
                Type.Str()
            },
            SourceSpan(span.start, endSpan),
        )
    }

    private fun fields(delimiter: Pair<Char, Char>): List<FieldSyntax> = delimited(delimiter.first, delimiter.second) { field() }.second

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
        var payload: List<FieldSyntax> = emptyList()
        val emptyPayloadSpanStart = here.start
        if (peek('(')) {
            payload =
                fields('(' to ')').ifEmpty { fail(SyntaxErrorKind.EmptyPayload(name), SourceSpan(emptyPayloadSpanStart, prior.span.end)) }
        }
        if (!accept('=')) return MemberSyntax(name, span, autoTag, null, payload)
        val tagSpan = here
        return MemberSyntax(name, span, int().exactInt(), tagSpan, payload)
    }

    private fun <T> angled(inner: () -> T): Triple<Token.Symbol, T, Token.Symbol> = Triple(expect('<'), inner(), expect('>'))

    private fun seq(keywordSpan: SourceSpan): TypeSyntax.Seq {
        val type = angled { type() }
        val newSpan = SourceSpan(keywordSpan.start, type.third.span.end)
        return TypeSyntax.Seq(type.second, newSpan)
    }

    private fun map(keywordSpan: SourceSpan): TypeSyntax.Map {
        val (_, types, closing) = delimited('<', '>') { type() }
        val newSpan = SourceSpan(keywordSpan.start, closing.span.end)
        if (types.size < 2) fail(ExpectedWord(closing), types.getOrNull(0)?.span ?: SourceSpan(closing.span.start, closing.span.end))
        if (types.size > 2) fail(ExpectedSymbol('>', Token.Word(types[2].toType().kind, types[2].span)), types[2].span)
        return TypeSyntax.Map(types.first(), types[1], newSpan)
    }

    private fun decimal(keywordSpan: SourceSpan): TypeSyntax.Primitive {
        expect('(')
        val firstSpan = here
        val first = (if (accept('-')) -BigInteger.ONE else BigInteger.ONE) * int().bigInt
        val (min, max) =
            if (peek(',')) {
                if (first !in BigInteger.ZERO..BigInteger(38)) fail(PrecisionOutOfRange(first), firstSpan)
                val bound = BigInteger.TEN.pow(first).dec()
                -bound to bound
            } else if (accept('.') && accept('.')) {
                if (peek('.')) fail(ExpectedDecimalBounds(current), current.span)
                val max = (if (accept('-')) -BigInteger.ONE else BigInteger.ONE) * int().bigInt
                ShapeErrorKind.EmptyRange
                    .check(first, max)
                    .firstOrNull()
                    ?.let { fail(InvalidType(it), firstSpan) }
                first to max
            } else {
                fail(ExpectedDecimalBounds(current), current.span)
            }
        expect(',')
        val scale = int().exactInt()
        val (rounding, overflow) = if (accept(',')) decimalOptions() else Rounding.TOWARDS_ZERO to Overflow.ERROR
        val endSpan = expect(')').span.end
        return TypeSyntax.Primitive(Type.Decimal(min, max, scale, rounding, overflow), SourceSpan(keywordSpan.start, endSpan))
    }

    private fun decimalShorthand(
        keywordSpan: SourceSpan,
        min: BigInteger,
        max: BigInteger,
        scale: Int = 0,
    ): TypeSyntax.Primitive {
        var endSpan = keywordSpan.end
        val (rounding, overflow) =
            if (accept('(')) decimalOptions().also { endSpan = expect(')').span.end } else Rounding.TOWARDS_ZERO to Overflow.ERROR
        return TypeSyntax.Primitive(Type.Decimal(min, max, scale, rounding, overflow), SourceSpan(keywordSpan.start, endSpan))
    }

    private fun decimalOptions(): Pair<Rounding, Overflow> {
        var rounding: Rounding? = null
        var overflow: Overflow? = null
        do {
            if (rounding == null) {
                tryEnumWord<Rounding>()?.let {
                    rounding = it
                    continue
                }
            }
            if (overflow == null) {
                tryEnumWord<Overflow>()?.let {
                    overflow = it
                    continue
                }
            }
            val (text, span) = word()
            val expected =
                buildList {
                    if (rounding == null) addAll(Rounding.entries.map { it.name })
                    if (overflow == null) addAll(Overflow.entries.map { it.name })
                }
            fail(ExpectedOption(expected, text), span)
        } while ((rounding == null || overflow == null) && accept(','))
        return (rounding ?: Rounding.TOWARDS_ZERO) to (overflow ?: Overflow.ERROR)
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
