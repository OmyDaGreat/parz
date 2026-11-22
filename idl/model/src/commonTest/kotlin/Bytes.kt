package xyz.malefic.parz.idl.model

import xyz.malefic.parz.idl.model.Reason.Fit.InexactLength
import xyz.malefic.parz.idl.model.Reason.Fit.TooLong
import xyz.malefic.parz.idl.model.Reason.Fit.TooShort
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyRange
import xyz.malefic.parz.idl.model.ShapeErrorKind.NegativeLength
import xyz.malefic.parz.idl.model.Type.Primitive.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `bytes`: any length, `bytes(n)` exactly n, `bytes(min..max)` in a range.
 */
class Bytes {
    private fun bytes(vararg values: Int) = Value.Bytes(values.map { it.toByte() }.toByteArray())

    private fun bytesOfSize(size: Int) = Value.Bytes(ByteArray(size))

    private val any = Bytes(0L..Long.MAX_VALUE)

    @Test fun nonBytes() = any.assertRejectsOtherKinds()

    @Test
    fun constructors() {
        assertEquals(Bytes(3, 3), Bytes(3L))
        assertEquals(Bytes(1, 5), Bytes(1L..5L))
        assertTrue(Bytes(3L).exact)
        assertTrue(Bytes(0L).exact)
        assertFalse(Bytes(1L..5L).exact)
        assertFalse(any.exact)
        assertEquals("bytes", any.kind)
    }

    @Test
    fun invalidShapes() {
        assertFailsWith<IllegalArgumentException> { Bytes(-1L) }
        assertFailsWith<IllegalArgumentException> { Bytes(-1L..5L) }
        assertFailsWith<IllegalArgumentException> { Bytes(5L..2L) }
        assertEquals(listOf(NegativeLength(-1)), NegativeLength.check(-1))
        assertEquals(emptyList(), NegativeLength.check(0))
        assertEquals("IDL127", NegativeLength(-1).code)
        assertEquals("Length -1 must be non-negative", NegativeLength(-1).message)
        assertEquals(listOf(EmptyRange(5L.toBigInteger(), 2L.toBigInteger())), EmptyRange.check(5L, 2L))
    }

    @Test
    fun zeroLengthsAreAllowed() {
        Bytes(0L).assertAccepts(bytes())
        Bytes(0L).assertRejects(bytes(1))
        Bytes(0L..2L).assertAccepts(bytes(), bytes(1), bytes(1, 2))
    }

    @Test
    fun anyLength() = any.assertAccepts(bytes(), bytes(0), bytesOfSize(10_000))

    @Test
    fun exactLength() {
        Bytes(2L).assertAccepts(bytes(0xc1, 0xc2), bytes(0, 0))
        assertEquals(listOf(mismatch(InexactLength(2, 1))), Bytes(2L).check(bytes(1)))
        assertEquals(listOf(mismatch(InexactLength(2, 3))), Bytes(2L).check(bytes(1, 2, 3)))
    }

    @Test
    fun lengthRange() {
        val type = Bytes(1L..3L)
        type.assertAccepts(bytes(1), bytes(1, 2), bytes(1, 2, 3))
        assertEquals(listOf(mismatch(TooShort(1, 0))), type.check(bytes()))
        assertEquals(listOf(mismatch(TooLong(3, 4))), type.check(bytes(1, 2, 3, 4)))
    }

    @Test
    fun valuesCompareByContent() {
        assertEquals(bytes(1, 2), bytes(1, 2))
        assertEquals(bytes(1, 2).hashCode(), bytes(1, 2).hashCode())
        assertNotEquals(bytes(1, 2), bytes(2, 1))
        assertNotEquals(bytes(1), bytes(1, 0))
        assertEquals(bytes(), Value.Bytes(ByteArray(0)))
    }

    @Test
    fun valuesWorkAsMapKeys() = assertEquals(2, mapOf(bytes(1) to 1, bytes(1) to 2, bytes(2) to 3).size)

    @Test
    fun valuesCannotBeChangedFromOutside() {
        val source = byteArrayOf(1, 2)
        val value = Value.Bytes(source)
        source[0] = 9
        assertEquals(bytes(1, 2), value)
        value.toByteArray()[0] = 9
        assertEquals(bytes(1, 2), value)
    }

    @Test
    fun hex() {
        assertEquals("", bytes().hex())
        assertEquals("00017f80ff", bytes(0, 1, 0x7f, 0x80, 0xff).hex())
        assertEquals("c1c2", bytes(0xc1, 0xc2).toString())
        assertEquals(3, bytes(1, 2, 3).size)
    }
}

private fun Long.toBigInteger() =
    com.ionspin.kotlin.bignum.integer
        .BigInteger(this)
