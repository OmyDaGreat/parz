package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
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
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.IntegerOutOfRange
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.InvalidType
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.PrecisionOutOfRange
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.UnexpectedCharacter

fun parse(tokens: context(Sink<Diagnostic<SyntaxErrorKind>>) () -> List<Token>) =
    collecting<SchemaSyntax, Diagnostic<SyntaxErrorKind>> {
        Parser(tokens(), contextOf<Sink<Diagnostic<SyntaxErrorKind>>>()).schema()
    }

@Suppress("ObjectInheritsException")
private object Abort : Exception()

internal class Parser(
    private val tokens: List<Token>,
    private val sink: Sink<Diagnostic<SyntaxErrorKind>>,
) {
    private var pos = 0

    private val current get() = tokens[pos]
    private val here get() = current.offset
    private val done get() = current is Token.EOF

    private fun fail(
        kind: SyntaxErrorKind,
        at: Token,
    ): Nothing {
        sink.report(Diagnostic(kind, at.span))
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
        val token = word()
        val (text, offset) = token
        return when (text) {
            "void" -> TypeExpr.Primitive(Type.Void, offset)
            "bool" -> TypeExpr.Primitive(Type.Bool, offset)
            "string" -> str(offset)
            "decimal" -> decimal(offset)
            "option" -> TypeExpr.Option(angled { type() }, offset)
            "seq" -> TypeExpr.Seq(angled { type() }, offset)
            "record", "enum" -> fail(SyntaxErrorKind.InlineDecl(text), token)
            else -> TypeExpr.Named(text, offset)
        }
    }

    private fun decl(): DeclSyntax {
        val (keyword, _) = word()
        val (name, offset) = word()
        return when (keyword) {
            "record" -> DeclSyntax.Record(name, offset, fields())
            "enum" -> DeclSyntax.Enum(name, offset, members())
            "type" -> expect('=').let { DeclSyntax.Alias(name, offset, type()) }
            else -> fail(ExpectedDeclKeyword(current), current)
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
        if (!accept(c)) fail(ExpectedSymbol(c, current), current)
    }

    private fun word(): Token.Word {
        val token = current as? Token.Word ?: fail(ExpectedWord(current), current)
        pos++
        return token
    }

    private fun int(): BigInteger {
        val token = current as? Token.Number ?: fail(ExpectedInt(current), current)
        pos++
        return BigInteger.parseString(token.text)
    }

    private inline fun <reified T : Enum<T>> enumWord(): T {
        val token = word()
        return enumValues<T>().find { it.name.equals(token.text, ignoreCase = true) }
            ?: fail(ExpectedOption(enumValues<T>().map { it.name }, token.text), token)
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

    private fun str(offset: Int) =
        TypeExpr.Primitive(
            checkSyntax("string", offset) {
                if (accept('(')) Type.Str(int().exactLong().also { expect(')') }) else Type.Str()
            },
            offset,
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
        val (name, offset) = word()
        expect(':')
        return FieldSyntax(name, offset, type())
    }

    private fun member(autoTag: Int): MemberSyntax {
        val (name, offset) = word()
        if (!accept('=')) return MemberSyntax(name, offset, autoTag, null)
        val tagOffset = here
        return MemberSyntax(name, offset, int().exactInt(), tagOffset)
    }

    private fun <T> angled(inner: () -> T): T {
        expect('<')
        return inner().also { expect('>') }
    }

    private fun decimal(offset: Int): TypeExpr.Primitive {
        expect('(')
        var signum = 1
        if (accept('-')) {
            signum = -1
        }
        val precisionOrMin = int() * signum
        return TypeExpr.Primitive(
            checkSyntax("decimal", offset) {
                if (accept(',')) {
                    val scale = int()
                    val rounding = if (accept(',')) enumWord<Rounding>() else Rounding.TOWARDS_ZERO
                    val overflow = if (accept(',')) enumWord<Overflow>() else Overflow.ERROR
                    expect(')')
                    if (precisionOrMin !in BigInteger.ZERO..BigInteger(38)) fail(PrecisionOutOfRange(precisionOrMin), current)
                    val bound = BigInteger.TEN.pow(precisionOrMin) - BigInteger.ONE
                    Type.Decimal(-bound, bound, scale.exactInt(), rounding, overflow)
                } else if (accept('.') && accept('.') && accept('.')) {
                    signum = if (accept('-')) -1 else 1
                    val max = int() * signum
                    expect(',')
                    val scale = int()
                    val rounding = if (accept(',')) enumWord<Rounding>() else Rounding.TOWARDS_ZERO
                    val overflow = if (accept(',')) enumWord<Overflow>() else Overflow.ERROR
                    expect(')')
                    Type.Decimal(precisionOrMin, max, scale.exactInt(), rounding, overflow)
                } else {
                    fail(ExpectedDecimalBounds(current), current)
                }
            },
            offset,
        )
    }

    private fun <T> checkSyntax(
        name: String,
        index: Int,
        type: () -> T,
    ): T =
        try {
            type()
        } catch (e: IllegalArgumentException) {
            fail(InvalidType(name, e.message), Token.Word(name, index))
        }

    private fun BigInteger.exactInt(): Int =
        try {
            intValue(exactRequired = true)
        } catch (_: ArithmeticException) {
            fail(IntegerOutOfRange(this, "Int"), current)
        }

    private fun BigInteger.exactLong(): Long =
        try {
            longValue(exactRequired = true)
        } catch (_: ArithmeticException) {
            fail(IntegerOutOfRange(this, "Long"), current)
        }
}
