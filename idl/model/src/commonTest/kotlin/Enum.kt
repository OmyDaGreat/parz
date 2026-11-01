package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Enum.Member
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Enum {
    private val single = enum(Member("key", 0))
    private val pair = enum(Member("key", 0), Member("key2", 1))

    @Test fun nonEnum() = single.assertRejectsOtherKinds("enum")

    @Test
    fun invalidEnum() {
        assertFailsWith<IllegalArgumentException> { enum() }
        assertFailsWith<IllegalArgumentException> { enum(Member("key", 1), Member("key", 0)) }
        assertFailsWith<IllegalArgumentException> { enum(Member("key1", 0), Member("key2", 0)) }
        single.assertRejects(Value.Enum(""))
    }

    @Test
    fun isEnum() {
        single.assertAccepts(Value.Enum("key"))
        pair.assertAccepts(Value.Enum("key"), Value.Enum("key2"))
    }

    @Test
    fun unknownMember() {
        val (schema, ref) = pair
        assertEquals(listOf(mismatch(Reason.UnknownMember("key3"))), ref.check(Value.Enum("key3"), schema))
    }
}
