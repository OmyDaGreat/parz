package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyRange
import xyz.malefic.parz.idl.model.ShapeErrorKind.NonPositiveLength
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Collection.Option
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Overflow
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Rounding
import xyz.malefic.parz.idl.model.Type.Primitive.Str
import xyz.malefic.parz.idl.model.Type.Primitive.Void
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedDecimalBounds
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedInt
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedOption
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedSymbol
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedWord
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.InlineDecl
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.IntegerOutOfRange
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.InvalidType
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.PrecisionOutOfRange
import kotlin.test.Test
import kotlin.test.assertEquals

class Types {
    private fun int(value: Long) = BigInteger(value)

    @Test
    fun primitives() {
        assertEquals(Void, typeOf("void").toType())
        assertEquals(Bool, typeOf("bool").toType())
        assertEquals(Str(), typeOf("string").toType())
        assertEquals(Str(10), typeOf("string(10)").toType())
    }

    @Test
    fun named() = assertEquals(Type.Ref("Money"), typeOf("Money").toType())

    @Test
    fun containers() {
        assertEquals(Option(Bool), typeOf("option<bool>").toType())
        assertEquals(Seq(Option(Type.Ref("Money"))), typeOf("seq<option<Money>>").toType())
    }

    @Test
    fun decimalPrecision() = assertEquals(Decimal(int(-99999), int(99999), 2), typeOf("decimal(5, 2)").toType())

    @Test
    fun decimalZeroPrecision() = assertEquals(Decimal(int(0), int(0), 0), typeOf("decimal(0, 0)").toType())

    @Test
    fun decimalPoliciesIgnoreCase() =
        assertEquals(
            Decimal(int(-99), int(99), 0, Rounding.ROUND_HALF_TO_EVEN, Overflow.SATURATE),
            typeOf("decimal(2, 0, round_half_to_even, Saturate)").toType(),
        )

    @Test
    fun decimalRange() {
        assertEquals(Decimal(int(-5), int(10), 1), typeOf("decimal(-5..10, 1)").toType())
        assertEquals(Decimal(int(-10), int(-5), 0, Rounding.FLOOR), typeOf("decimal(-10..-5, 0, floor)").toType())
        assertEquals(Decimal(int(3), int(3), 0), typeOf("decimal(3..3, 0)").toType())
    }

    @Test
    fun namedSpan() {
        val source = "type T = seq<option<Money>>"
        val seq = decl<DeclSyntax.Alias>(source).type as TypeSyntax.Seq
        val option = seq.inner as TypeSyntax.Option
        assertEquals(source.span("Money"), option.inner.span)
    }

    @Test
    fun seqSpan() {
        val source = "type T = seq<bool>"
        assertEquals(source.span("seq<bool>"), decl<DeclSyntax.Alias>(source).type.span)
    }

    @Test
    fun optionSpan() {
        val source = "type T = option<bool>"
        assertEquals(source.span("option<bool>"), decl<DeclSyntax.Alias>(source).type.span)
    }

    @Test
    fun decimalSpan() {
        val source = "type T = decimal(5, 2)"
        assertEquals(source.span("decimal(5, 2)"), decl<DeclSyntax.Alias>(source).type.span)
    }

    @Test
    fun stringSpan() {
        val source = "type T = string(10)"
        assertEquals(source.span("string(10)"), decl<DeclSyntax.Alias>(source).type.span)
    }

    @Test
    fun precisionOutOfRange() {
        val source = "type T = decimal(39, 0)"
        val error = error(source)
        assertEquals(PrecisionOutOfRange(int(39)), error.kind)
        assertEquals(source.span("39"), error.span)
    }

    @Test
    fun emptyRange() {
        val source = "type T = decimal(5..1, 0)"
        val error = error(source)
        assertEquals(InvalidType(EmptyRange(int(5), int(1))), error.kind)
        assertEquals("IDL124", error.kind.code)
        assertEquals(source.span("5").start, error.span?.start)
    }

    @Test
    fun nonPositiveLength() {
        val source = "type T = string(0)"
        val error = error(source)
        assertEquals(InvalidType(NonPositiveLength(0)), error.kind)
        assertEquals("IDL125", error.kind.code)
        assertEquals(source.span("0"), error.span)
    }

    @Test
    fun missingDecimalBounds() {
        val source = "type T = decimal(9)"
        assertEquals(ExpectedDecimalBounds(Token.Symbol(')', source.span(")"))), error(source).kind)
    }

    @Test
    fun unknownRounding() {
        val source = "type T = decimal(9, 2, banana)"
        val error = error(source)
        assertEquals(
            ExpectedOption(Rounding.entries.map { it.name } + Overflow.entries.map { it.name }, "banana"),
            error.kind,
        )
        assertEquals(source.span("banana"), error.span)
    }

    @Test
    fun unknownOverflow() {
        val source = "type T = decimal(9, 2, floor, banana)"
        assertEquals(ExpectedOption(Overflow.entries.map { it.name }, "banana"), error(source).kind)
    }

    @Test
    fun negativeScale() {
        val source = "type T = decimal(9, -2)"
        assertEquals(ExpectedInt(Token.Symbol('-', source.span("-"))), error(source).kind)
    }

    @Test
    fun integersOutOfRange() {
        val scale = "type T = decimal(9, 99999999999)"
        assertEquals(IntegerOutOfRange(BigInteger.parseString("99999999999"), "Int"), error(scale).kind)
        assertEquals(scale.span("99999999999"), error(scale).span)
        val length = "type T = string(99999999999999999999)"
        assertEquals(IntegerOutOfRange(BigInteger.parseString("99999999999999999999"), "Long"), error(length).kind)
    }

    @Test
    fun missingInnerType() {
        val source = "type T = option<>"
        assertEquals(ExpectedWord(Token.Symbol('>', source.span(">"))), error(source).kind)
    }

    @Test
    fun unclosedAngle() {
        val source = "type T = seq<bool"
        assertEquals(ExpectedSymbol('>', Token.EOF(source.length)), error(source).kind)
    }

    @Test
    fun inlineDecls() {
        listOf("record", "variant", "enum", "type").forEach { keyword ->
            val source = "type T = $keyword"
            val error = error(source)
            assertEquals(InlineDecl(keyword), error.kind)
            assertEquals(SourceSpan(9, 9 + keyword.length), error.span)
        }
    }
}
