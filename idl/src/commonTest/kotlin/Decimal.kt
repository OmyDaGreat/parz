import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ONE
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.TEN
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.TWO
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import xyz.malefic.parz.idl.Mismatch
import xyz.malefic.parz.idl.Reason
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Decimal.Rounding
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.accepts
import xyz.malefic.parz.idl.check
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
        assertEquals(
            Type.Decimal(ZERO, BigInteger(100), 2).check(Value.Decimal(BigInteger(1004), 3)),
            listOf(Mismatch(listOf(), Reason.OutOfRange(ZERO, BigInteger(100)))),
        )
        assertEquals(
            Type.Decimal(ZERO, BigInteger(100), 2, Rounding.PROHIBITED).check(Value.Decimal(BigInteger(1004), 3)),
            listOf(Mismatch(listOf(), Reason.InexactScale(2, 3))),
        )
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
}
