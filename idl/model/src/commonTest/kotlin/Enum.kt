package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ONE
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import xyz.malefic.parz.idl.Type.Enum.Member
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Enum {
    private val single = Type.Enum(Member("key", 0))
    private val pair = Type.Enum(Member("key", 0), Member("key2", 1))

    @Test fun nonEnum() = single.assertRejectsOtherKinds()

    @Test
    fun invalidEnum() {
        assertFailsWith<IllegalArgumentException> { Type.Enum() }
        assertFailsWith<IllegalArgumentException> { Type.Enum(Member("key", 1), Member("key", 0)) }
        assertFailsWith<IllegalArgumentException> { Type.Enum(Member("key1", 0), Member("key2", 0)) }
        single.assertRejects(Value.Enum(""))
    }

    @Test
    fun isEnum() {
        single.assertAccepts(Value.Enum("key"))
        pair.assertAccepts(Value.Enum("key"), Value.Enum("key2"))
    }
}
