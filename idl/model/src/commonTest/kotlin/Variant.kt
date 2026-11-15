package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Variant.Member
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Variant {
    private val single = variant(Member("key", 0))
    private val pair = variant(Member("key", 0), Member("key2", 1))

    @Test fun nonVariant() = single.assertRejectsOtherKinds("variant")

    @Test
    fun invalidVariant() {
        assertFailsWith<IllegalArgumentException> { variant() }
        assertFailsWith<IllegalArgumentException> { variant(Member("key", 1), Member("key", 0)) }
        assertFailsWith<IllegalArgumentException> { variant(Member("key1", 0), Member("key2", 0)) }
        single.assertRejects(Value.Variant(""))
    }

    @Test
    fun isVariant() {
        single.assertAccepts(Value.Variant("key"))
        pair.assertAccepts(Value.Variant("key"), Value.Variant("key2"))
    }

    @Test
    fun unknownMember() {
        val (schema, ref) = pair
        assertEquals(listOf(mismatch(Reason.UnknownMember("key3"))), ref.check(Value.Variant("key3"), schema))
    }
}
