package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyName
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyRange
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyRecord
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyVariant
import xyz.malefic.parz.idl.ShapeErrorKind.EnumPayload
import xyz.malefic.parz.idl.ShapeErrorKind.NegativeTag
import xyz.malefic.parz.idl.ShapeErrorKind.NonPositiveLength
import xyz.malefic.parz.idl.Type.Primitive.Bool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Shape errors: problems a single declaration or type has on its own, checked on construction.
 */
class Shape {
    @Test
    fun checks() {
        assertEquals(listOf(EmptyRecord), EmptyRecord.check(emptyList<Field>()))
        assertEquals(emptyList(), EmptyRecord.check(listOf(Field("x", Bool))))
        assertEquals(listOf(EmptyVariant), EmptyVariant.check(emptyList<Member>()))
        assertEquals(listOf(EmptyName), EmptyName.check(""))
        assertEquals(listOf(EmptyName), EmptyName.check("  "))
        assertEquals(emptyList(), EmptyName.check("x"))
        assertEquals(listOf(NegativeTag(-1)), NegativeTag.check(-1))
        assertEquals(emptyList(), NegativeTag.check(0))
        assertEquals(listOf(EmptyRange(BigInteger(2), BigInteger(1))), EmptyRange.check(BigInteger(2), BigInteger(1)))
        assertEquals(emptyList(), EmptyRange.check(BigInteger(1), BigInteger(1)))
        assertEquals(listOf(NonPositiveLength(0)), NonPositiveLength.check(0))
        assertEquals(emptyList(), NonPositiveLength.check(1))
        val withField = Member("a", 0, listOf(Field("x", Bool)))
        assertEquals(listOf(EnumPayload(withField)), EnumPayload.check(withField))
        assertEquals(emptyList(), EnumPayload.check(Member("a", 0)))
    }

    @Test
    fun constructorsRejectBadShapes() {
        assertFailsWith<IllegalArgumentException> { Decl.Record("R", emptyList()) }
        assertFailsWith<IllegalArgumentException> { Decl.Variant("V", emptyList()) }
        assertFailsWith<IllegalArgumentException> { Field("", Bool) }
        assertFailsWith<IllegalArgumentException> { Field("  ", Bool) }
        assertFailsWith<IllegalArgumentException> { Member("", 0) }
        assertFailsWith<IllegalArgumentException> { Member("m", -1) }
        assertFailsWith<IllegalArgumentException> {
            Decl.Variant("E", listOf(Member("a", 0, listOf(Field("x", Bool)))), isEnum = true)
        }
    }

    @Test
    fun declNamesMustNotBeEmpty() {
        assertFailsWith<IllegalArgumentException> { Decl.Record("", listOf(Field("x", Bool))) }
        assertFailsWith<IllegalArgumentException> { Decl.Variant("", listOf(Member("a", 0))) }
        assertFailsWith<IllegalArgumentException> { Decl.Alias("", Bool) }
    }

    @Test
    fun messages() {
        assertEquals("Name must not be empty", EmptyName.message)
        assertEquals("Variant member tag -1 must be non-negative", NegativeTag(-1).message)
        assertEquals("Payload of variant member 'a' must be empty when declared as enum", EnumPayload(Member("a", 0)).message)
        assertEquals("Range must not be empty or negative: [2, 1]", EmptyRange(BigInteger(2), BigInteger(1)).message)
    }
}
