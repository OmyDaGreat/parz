package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ONE
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.TEN
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.TWO
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import xyz.malefic.parz.idl.Type.Decimal.Overflow
import xyz.malefic.parz.idl.Type.Decimal.Rounding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Decimal {
    @Test
    fun nonDecimal() {
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Void }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Bool(true) }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Str("") }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Seq(emptyList()) }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Record(emptyMap()) }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Enum("") }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Some(Value.Void) }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.None }
    }

    @Test
    fun invalidDecimal() {
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Decimal(ONE, 0) }
        assertFalse { Type.Decimal(ZERO, ZERO, 0) accepts Value.Decimal(-ONE, 0) }
        assertFalse { Type.Decimal(-TEN, TEN, 0) accepts Value.Decimal(TEN + 1, 0) }
        assertFalse { Type.Decimal(-TEN, TEN, 0) accepts Value.Decimal(-TEN - 1, 0) }
        assertFalse { Type.Decimal(ZERO, TEN, 2) accepts Value.Decimal(-ONE, 2) }
        assertFalse { Type.Decimal(-TWO.pow(128), TWO.pow(128), 0) accepts Value.Decimal(TWO.pow(128) + 1, 0) }
        assertFalse { Type.Decimal(ZERO, TEN.pow(2), 2) accepts Value.Decimal(BigInteger(1004), 3) }
        assertEquals(
            Type.Decimal(ZERO, TEN.pow(2), 2).check(Value.Decimal(BigInteger(1004), 3)),
            listOf(Mismatch(listOf(), Reason.InexactScale(2, 3)), Mismatch(listOf(), Reason.OutOfRange(ZERO, TEN.pow(2)))),
        )
        assertEquals(
            Type.Decimal(ZERO, TEN.pow(2), 2, Rounding.PROHIBITED).check(Value.Decimal(BigInteger(1004), 3)),
            listOf(Mismatch(listOf(), Reason.InexactScale(2, 3)), Mismatch(listOf(), Reason.OutOfRange(ZERO, TEN.pow(2)))),
        )
        assertEquals(
            Type.Decimal(ZERO, TEN.pow(5), 2, Rounding.ROUND_HALF_AWAY_FROM_ZERO).check(Value.Decimal(BigInteger(500001), 3)),
            listOf(Mismatch(listOf(), Reason.InexactScale(2, 3))),
        )
        assertEquals(
            Type.Decimal(ZERO, TEN.pow(5), 2, overflow = Overflow.SATURATE).check(Value.Decimal(TEN.pow(5) + 1, 2)),
            listOf(Mismatch(listOf(), Reason.OutOfRange(ZERO, TEN.pow(5)))),
        )
        assertFalse { Type.Decimal(-TEN, TEN, 0, overflow = Overflow.WRAP) accepts Value.Decimal(TEN + 1, 0) }
        assertFails { Type.Decimal(ONE, ZERO, 0) accepts Value.Decimal(ZERO, 0) }
        assertFails { Type.Decimal(TEN, -TEN, 2) accepts Value.Decimal(ZERO, 2) }
    }

    @Test
    fun isDecimal() {
        assertTrue { Type.Decimal(-TEN, TEN, 0) accepts Value.Decimal(ZERO, 0) }
        assertTrue { Type.Decimal(-TEN, TEN, 0) accepts Value.Decimal(ONE, 0) }
        assertTrue { Type.Decimal(-TEN, TEN, 0) accepts Value.Decimal(-ONE, 0) }
        assertTrue { Type.Decimal(-TEN, TEN, 0) accepts Value.Decimal(TEN, 0) }
        assertTrue { Type.Decimal(-TEN, TEN, 0) accepts Value.Decimal(-TEN, 0) }
        assertTrue { Type.Decimal(ZERO, ZERO, 0) accepts Value.Decimal(ZERO, -1) }
        assertTrue { Type.Decimal(-ONE, ONE, 0) accepts Value.Decimal(TEN, 1) }
        assertTrue { Type.Decimal(-TEN.pow(4), TEN.pow(4), 2) accepts Value.Decimal(BigInteger(1234), 2) }
        assertTrue { Type.Decimal(-TEN.pow(4), TEN.pow(4), 2) accepts Value.Decimal(-TEN.pow(4), 2) }
        assertTrue { Type.Decimal(ONE, ONE, 3) accepts Value.Decimal(ONE, 3) }
        assertTrue { Type.Decimal(-TWO.pow(128), TWO.pow(128), 0) accepts Value.Decimal(TWO.pow(128), 0) }
    }

    @Test
    fun rescaleRounding() {
        val inputs = listOf(55, 25, 16, 11, 10, 5, 1, -1, -5, -10, -11, -16, -25, -55).map { Value.Decimal(BigInteger(it), 1) }
        val expected =
            mapOf(
                Rounding.AWAY_FROM_ZERO to listOf(6, 3, 2, 2, 1, 1, 1, -1, -1, -1, -2, -2, -3, -6),
                Rounding.TOWARDS_ZERO to listOf(5, 2, 1, 1, 1, 0, 0, 0, 0, -1, -1, -1, -2, -5),
                Rounding.CEILING to listOf(6, 3, 2, 2, 1, 1, 1, 0, 0, -1, -1, -1, -2, -5),
                Rounding.FLOOR to listOf(5, 2, 1, 1, 1, 0, 0, -1, -1, -1, -2, -2, -3, -6),
                Rounding.ROUND_HALF_AWAY_FROM_ZERO to listOf(6, 3, 2, 1, 1, 1, 0, 0, -1, -1, -1, -2, -3, -6),
                Rounding.ROUND_HALF_TOWARDS_ZERO to listOf(5, 2, 2, 1, 1, 0, 0, 0, 0, -1, -1, -2, -2, -5),
                Rounding.ROUND_HALF_CEILING to listOf(6, 3, 2, 1, 1, 1, 0, 0, 0, -1, -1, -2, -2, -5),
                Rounding.ROUND_HALF_FLOOR to listOf(5, 2, 2, 1, 1, 0, 0, 0, -1, -1, -1, -2, -3, -6),
                Rounding.ROUND_HALF_TO_EVEN to listOf(6, 2, 2, 1, 1, 0, 0, 0, 0, -1, -1, -2, -2, -6),
                Rounding.ROUND_HALF_TO_ODD to listOf(5, 3, 2, 1, 1, 1, 0, 0, -1, -1, -1, -2, -3, -5),
            )
        expected.forEach { (rounding, outputs) ->
            inputs.zip(outputs).forEach { (input, output) ->
                assertEquals(Value.Decimal(BigInteger(output), 0), input.rescale(0, rounding), "$input with $rounding")
            }
        }

        assertEquals(Value.Decimal(ONE, 0), Value.Decimal(BigInteger(5), 1).rescale(0, Rounding.ROUND_HALF_TO_ODD))
        assertEquals(Value.Decimal(-ONE, 0), Value.Decimal(BigInteger(-5), 1).rescale(0, Rounding.ROUND_HALF_TO_ODD))
        assertEquals(Value.Decimal(ZERO, 0), Value.Decimal(BigInteger(5), 1).rescale(0, Rounding.ROUND_HALF_TO_EVEN))
        assertEquals(Value.Decimal(BigInteger(123), 2), Value.Decimal(BigInteger(12349), 4).rescale(2, Rounding.ROUND_HALF_TO_EVEN))
        assertEquals(Value.Decimal(BigInteger(124), 2), Value.Decimal(BigInteger(12350), 4).rescale(2, Rounding.ROUND_HALF_TO_EVEN))
        assertEquals(Value.Decimal(BigInteger(124), 2), Value.Decimal(BigInteger(12450), 4).rescale(2, Rounding.ROUND_HALF_TO_EVEN))
        assertEquals(Value.Decimal(BigInteger(125), 2), Value.Decimal(BigInteger(124501), 5).rescale(2, Rounding.ROUND_HALF_TO_EVEN))
        assertEquals(Value.Decimal(ONE, -1), Value.Decimal(BigInteger(12), 0).rescale(-1, Rounding.ROUND_HALF_TO_EVEN))
        assertEquals(Value.Decimal(BigInteger(1500), 3), Value.Decimal(BigInteger(15), 1).rescale(3, Rounding.PROHIBITED))
        assertEquals(Value.Decimal(ONE, 0), Value.Decimal(TEN, 1).rescale(0, Rounding.PROHIBITED))
        assertFailsWith<ArithmeticException> { Value.Decimal(BigInteger(11), 1).rescale(0, Rounding.PROHIBITED) }
    }

    @Test
    fun coerce() {
        val max = TEN.pow(5) - 1
        val pic = { overflow: Overflow, rounding: Rounding -> Type.Decimal(-max, max, 2, rounding, overflow) }

        assertEquals(Value.Decimal(max, 2), pic(Overflow.ERROR, Rounding.TOWARDS_ZERO) coerce Value.Decimal(BigInteger(999991), 3))
        assertFailsWith<ArithmeticException> { pic(Overflow.ERROR, Rounding.TOWARDS_ZERO) coerce Value.Decimal(TEN.pow(5), 2) }
        assertFailsWith<ArithmeticException> {
            pic(Overflow.ERROR, Rounding.ROUND_HALF_AWAY_FROM_ZERO) coerce Value.Decimal(BigInteger(999995), 3)
        }
        assertFailsWith<ArithmeticException> { pic(Overflow.SATURATE, Rounding.PROHIBITED) coerce Value.Decimal(BigInteger(1234), 3) }

        assertEquals(
            Value.Decimal(max, 2),
            pic(Overflow.SATURATE, Rounding.ROUND_HALF_AWAY_FROM_ZERO) coerce Value.Decimal(BigInteger(999995), 3),
        )
        assertEquals(Value.Decimal(max, 2), pic(Overflow.SATURATE, Rounding.TOWARDS_ZERO) coerce Value.Decimal(BigInteger(123456), 2))
        assertEquals(Value.Decimal(-max, 2), pic(Overflow.SATURATE, Rounding.TOWARDS_ZERO) coerce Value.Decimal(BigInteger(-123456), 2))

        assertEquals(
            Value.Decimal(BigInteger(23456), 2),
            pic(Overflow.TRUNCATE_HIGH, Rounding.TOWARDS_ZERO) coerce Value.Decimal(BigInteger(123456), 2),
        )
        assertEquals(
            Value.Decimal(BigInteger(-23456), 2),
            pic(Overflow.TRUNCATE_HIGH, Rounding.TOWARDS_ZERO) coerce Value.Decimal(BigInteger(-123456), 2),
        )
        assertFailsWith<ArithmeticException> {
            Type.Decimal(ZERO, BigInteger(500), 0, overflow = Overflow.TRUNCATE_HIGH) coerce Value.Decimal(BigInteger(600), 0)
        }

        val byte = Type.Decimal(BigInteger(-128), BigInteger(127), 0, overflow = Overflow.WRAP)
        assertEquals(Value.Decimal(BigInteger(-128), 0), byte coerce Value.Decimal(BigInteger(128), 0))
        assertEquals(Value.Decimal(BigInteger(127), 0), byte coerce Value.Decimal(BigInteger(-129), 0))
        assertEquals(Value.Decimal(BigInteger(44), 0), byte coerce Value.Decimal(BigInteger(300), 0))
        assertEquals(Value.Decimal(BigInteger(5), 0), byte coerce Value.Decimal(BigInteger(5), 0))
    }
}
