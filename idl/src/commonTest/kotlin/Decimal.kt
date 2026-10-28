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
import kotlin.test.assertFailsWith

class Decimal {
    @Test
    fun nonDecimal() = Type.Decimal(ZERO, ZERO, 0).assertRejectsOtherKinds()

    @Test
    fun invalidBounds() {
        assertFailsWith<IllegalArgumentException> { Type.Decimal(ONE, ZERO, 0) }
        assertFailsWith<IllegalArgumentException> { Type.Decimal(TEN, -TEN, 2) }
    }

    @Test
    fun outOfRange() {
        Type.Decimal(ZERO, ZERO, 0).assertRejects(dec(1), dec(-1))
        Type.Decimal(-TEN, TEN, 0).assertRejects(dec(TEN + 1), dec(-TEN - 1))
        Type.Decimal(-TEN, TEN, 0, overflow = Overflow.WRAP).assertRejects(dec(TEN + 1))
        Type.Decimal(ZERO, TEN, 2).assertRejects(dec(-1, 2))
        Type.Decimal(ZERO, TEN.pow(2), 2).assertRejects(dec(1004, 3))
        Type.Decimal(-TWO.pow(128), TWO.pow(128), 0).assertRejects(dec(TWO.pow(128) + 1))
    }

    @Test
    fun mismatchReasons() {
        val inexactAndOutOfRange = listOf(mismatch(Reason.InexactScale(2, 3)), mismatch(Reason.OutOfRange(ZERO, TEN.pow(2))))
        assertEquals(inexactAndOutOfRange, Type.Decimal(ZERO, TEN.pow(2), 2) check dec(1004, 3))
        assertEquals(inexactAndOutOfRange, Type.Decimal(ZERO, TEN.pow(2), 2, Rounding.PROHIBITED) check dec(1004, 3))
        assertEquals(
            listOf(mismatch(Reason.InexactScale(2, 3))),
            Type.Decimal(ZERO, TEN.pow(5), 2, Rounding.ROUND_HALF_AWAY_FROM_ZERO) check dec(500001, 3),
        )
        assertEquals(
            listOf(mismatch(Reason.OutOfRange(ZERO, TEN.pow(5)))),
            Type.Decimal(ZERO, TEN.pow(5), 2, overflow = Overflow.SATURATE) check dec(TEN.pow(5) + 1, 2),
        )
    }

    @Test
    fun isDecimal() {
        Type.Decimal(-TEN, TEN, 0).assertAccepts(dec(0), dec(1), dec(-1), dec(TEN), dec(-TEN))
        Type.Decimal(ZERO, ZERO, 0).assertAccepts(dec(0, -1))
        Type.Decimal(-ONE, ONE, 0).assertAccepts(dec(10, 1))
        Type.Decimal(-TEN.pow(4), TEN.pow(4), 2).assertAccepts(dec(1234, 2), dec(-TEN.pow(4), 2))
        Type.Decimal(ONE, ONE, 3).assertAccepts(dec(1, 3))
        Type.Decimal(-TWO.pow(128), TWO.pow(128), 0).assertAccepts(dec(TWO.pow(128)))
    }

    @Test
    fun rescaleRounding() {
        val inputs = listOf(55, 25, 16, 11, 10, 5, 1, -1, -5, -10, -11, -16, -25, -55).map { dec(it, 1) }
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
                assertEquals(dec(output), input.rescale(0, rounding), "$input with $rounding")
            }
        }

        listOf(
            Triple(dec(12349, 4), 2, dec(123, 2)),
            Triple(dec(12350, 4), 2, dec(124, 2)),
            Triple(dec(12450, 4), 2, dec(124, 2)),
            Triple(dec(124501, 5), 2, dec(125, 2)),
            Triple(dec(12), -1, dec(1, -1)),
        ).forEach { (input, scale, output) ->
            assertEquals(output, input.rescale(scale, Rounding.ROUND_HALF_TO_EVEN), "$input to scale $scale")
        }

        assertEquals(dec(1500, 3), dec(15, 1).rescale(3, Rounding.PROHIBITED))
        assertEquals(dec(1), dec(10, 1).rescale(0, Rounding.PROHIBITED))
        assertFailsWith<ArithmeticException> { dec(11, 1).rescale(0, Rounding.PROHIBITED) }
    }

    @Test
    fun coerce() {
        val max = TEN.pow(5) - 1
        val pic = { overflow: Overflow, rounding: Rounding -> Type.Decimal(-max, max, 2, rounding, overflow) }

        val erroring = pic(Overflow.ERROR, Rounding.TOWARDS_ZERO)
        assertEquals(dec(max, 2), erroring coerce dec(999991, 3))
        assertFailsWith<ArithmeticException> { erroring coerce dec(TEN.pow(5), 2) }
        assertFailsWith<ArithmeticException> { pic(Overflow.ERROR, Rounding.ROUND_HALF_AWAY_FROM_ZERO) coerce dec(999995, 3) }

        val saturating = pic(Overflow.SATURATE, Rounding.TOWARDS_ZERO)
        assertEquals(dec(max, 2), saturating coerce dec(123456, 2))
        assertEquals(dec(-max, 2), saturating coerce dec(-123456, 2))
        assertEquals(dec(max, 2), pic(Overflow.SATURATE, Rounding.ROUND_HALF_AWAY_FROM_ZERO) coerce dec(999995, 3))
        assertFailsWith<ArithmeticException> { pic(Overflow.SATURATE, Rounding.PROHIBITED) coerce dec(1234, 3) }

        val truncating = pic(Overflow.TRUNCATE_HIGH, Rounding.TOWARDS_ZERO)
        assertEquals(dec(23456, 2), truncating coerce dec(123456, 2))
        assertEquals(dec(-23456, 2), truncating coerce dec(-123456, 2))
        assertFailsWith<ArithmeticException> {
            Type.Decimal(ZERO, BigInteger(500), 0, overflow = Overflow.TRUNCATE_HIGH) coerce dec(600)
        }

        val byte = Type.Decimal(BigInteger(-128), BigInteger(127), 0, overflow = Overflow.WRAP)
        assertEquals(dec(-128), byte coerce dec(128))
        assertEquals(dec(127), byte coerce dec(-129))
        assertEquals(dec(44), byte coerce dec(300))
        assertEquals(dec(5), byte coerce dec(5))
    }
}
