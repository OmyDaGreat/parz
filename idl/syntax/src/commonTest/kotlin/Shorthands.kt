package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Overflow
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Rounding
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedOption
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedSymbol
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The integer shorthands `u8`…`u64` and `i8`…`i64`: scale-0 decimals with the matching binary range, optionally with
 * rounding and overflow policies, and printed back as the shorthand whenever a decimal matches one exactly.
 */
class Shorthands {
    private fun two(power: Int) = BigInteger(2).pow(power)

    private val ranges =
        mapOf(
            "u8" to (BigInteger.ZERO to two(8).dec()),
            "u16" to (BigInteger.ZERO to two(16).dec()),
            "u32" to (BigInteger.ZERO to two(32).dec()),
            "u64" to (BigInteger.ZERO to two(64).dec()),
            "i8" to (two(7).negate() to two(7).dec()),
            "i16" to (two(15).negate() to two(15).dec()),
            "i32" to (two(31).negate() to two(31).dec()),
            "i64" to (two(63).negate() to two(63).dec()),
        )

    private fun assertRoundTrips(text: String) {
        val printed = typeOf(text).pretty()
        assertEquals(typeOf(text).toType(), typeOf(printed).toType(), "$text printed as $printed")
    }

    @Test
    fun eachRange() =
        ranges.forEach { (name, range) ->
            assertEquals(Decimal(range.first, range.second, 0), typeOf(name).toType(), name)
        }

    @Test
    fun rangeBoundaries() {
        assertEquals("255", (typeOf("u8").toType() as Decimal).max.toString())
        assertEquals("-9223372036854775808", (typeOf("i64").toType() as Decimal).min.toString())
        assertEquals("18446744073709551615", (typeOf("u64").toType() as Decimal).max.toString())
    }

    @Test
    fun policiesInEitherOrder() {
        val (min, max) = ranges.getValue("i32")
        assertEquals(Decimal(min, max, 0, Rounding.FLOOR), typeOf("i32(floor)").toType())
        assertEquals(Decimal(min, max, 0, overflow = Overflow.WRAP), typeOf("i32(wrap)").toType())
        assertEquals(Decimal(min, max, 0, Rounding.FLOOR, Overflow.WRAP), typeOf("i32(floor, wrap)").toType())
        assertEquals(Decimal(min, max, 0, Rounding.FLOOR, Overflow.WRAP), typeOf("i32(wrap, floor)").toType())
        assertEquals(Decimal(min, max, 0, Rounding.FLOOR), typeOf("i32(FLOOR)").toType())
    }

    @Test
    fun spans() {
        listOf("u8", "i32(wrap)", "u16(floor, saturate)").forEach { text ->
            val source = "type T = $text"
            assertEquals(source.span(text), decl<DeclSyntax.Alias>(source).type.span, text)
        }
    }

    @Test
    fun keywordsAreCaseSensitive() = assertEquals(Type.Ref("U8"), typeOf("U8").toType())

    @Test
    fun emptyParens() {
        val source = "type T = u8()"
        assertEquals(ExpectedWord(Token.Symbol(')', source.span(")"))), error(source).kind)
    }

    @Test
    fun unknownPolicy() {
        val source = "type T = i32(banana)"
        val error = error(source)
        assertEquals(ExpectedOption((Rounding.entries + Overflow.entries).map { it.name }, "banana"), error.kind)
        assertEquals(source.span("banana"), error.span)
    }

    @Test
    fun repeatedRoundingOffersOnlyOverflow() {
        val source = "type T = i32(floor, floor)"
        val error = error(source)
        assertEquals(ExpectedOption(Overflow.entries.map { it.name }, "floor"), error.kind)
        assertEquals(source.span("floor", 1), error.span)
    }

    @Test
    fun repeatedOverflowOffersOnlyRounding() {
        val source = "type T = i32(wrap, wrap)"
        assertEquals(ExpectedOption(Rounding.entries.map { it.name }, "wrap"), error(source).kind)
    }

    @Test
    fun tooManyPolicies() {
        val source = "type T = i32(floor, wrap, saturate)"
        assertIs<ExpectedSymbol>(error(source).kind)
        assertEquals(source.span(",", 1), error(source).span)
    }

    @Test
    fun missingComma() {
        assertIs<ExpectedSymbol>(error("type T = i32(floor wrap)").kind)
    }

    // Printing

    @Test
    fun eachPrintsAsItself() = ranges.keys.forEach { assertEquals(it, typeOf(it).pretty()) }

    @Test
    fun matchingDecimalsPrintAsTheShorthand() =
        ranges.forEach { (name, range) ->
            assertEquals(name, typeOf("decimal(${range.first}..${range.second}, 0)").pretty())
        }

    @Test
    fun policiesPrintInParens() {
        assertEquals("i8(wrap)", typeOf("i8(wrap)").pretty())
        assertEquals("i32(floor)", typeOf("i32(floor)").pretty())
        assertEquals("u16(floor, wrap)", typeOf("u16(wrap, floor)").pretty())
        assertEquals(
            "i64(round_half_to_even, saturate)",
            typeOf("decimal(${two(63).negate()}..${two(63).dec()}, 0, round_half_to_even, saturate)").pretty(),
        )
    }

    @Test
    fun defaultPoliciesAreDropped() = assertEquals("i8", typeOf("decimal(-128..127, 0, towards_zero, error)").pretty())

    @Test
    fun otherScalesKeepTheRange() {
        assertEquals("decimal(0..255, 1)", typeOf("decimal(0..255, 1)").pretty())
        assertEquals("decimal(-128..127, -1)", typeOf("decimal(-128..127, -1)").pretty())
    }

    @Test
    fun nearMissesKeepTheRange() {
        assertEquals("decimal(0..254, 0)", typeOf("decimal(0..254, 0)").pretty())
        assertEquals("decimal(-127..127, 0)", typeOf("decimal(-127..127, 0)").pretty())
        assertEquals("decimal(1..255, 0)", typeOf("decimal(1..255, 0)").pretty())
    }

    @Test
    fun precisionFormStillWins() {
        assertEquals("decimal(9, 2)", typeOf("decimal(9, 2)").pretty())
        assertEquals("decimal(2, 0)", typeOf("decimal(-99..99, 0)").pretty())
        assertEquals("decimal(9, 2, wrap)", typeOf("decimal(9, 2, wrap)").pretty())
        assertEquals("decimal(-5..5, 0)", typeOf("decimal(-5..5, 0)").pretty())
    }

    @Test
    fun roundTrips() =
        (
            ranges.keys +
                listOf("i8(wrap)", "i32(floor, wrap)", "u64(saturate)", "decimal(0..255, 1)", "decimal(9, -2)", "decimal(-5..5, 0, floor)")
        ).forEach(::assertRoundTrips)

    @Test
    fun insideAFullSchema() =
        assertEquals(
            "record A {\n  small: u8,\n  big: i64(wrap),\n  money: decimal(9, 2),\n}",
            parsed("record A { small: decimal(0..255, 0), big: i64(wrap), money: decimal(9, 2) }").pretty(),
        )
}
