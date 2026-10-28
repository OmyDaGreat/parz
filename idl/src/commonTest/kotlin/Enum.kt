package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ONE
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import xyz.malefic.parz.idl.Type.Enum.Member
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Enum {
    private val single = Type.Enum(Member("key", ZERO))
    private val pair = Type.Enum(Member("key", ZERO), Member("key2", ONE))

    @Test fun nonEnum() = single.assertRejectsOtherKinds()

    @Test
    fun invalidEnum() {
        assertFailsWith<IllegalArgumentException> { Type.Enum() }
        assertFailsWith<IllegalArgumentException> { Type.Enum(Member("key", ONE), Member("key", ZERO)) }
        assertFailsWith<IllegalArgumentException> { Type.Enum(Member("key1", ZERO), Member("key2", ZERO)) }
        single.assertRejects(Value.Enum(""))
    }

    @Test
    fun isEnum() {
        single.assertAccepts(Value.Enum("key"))
        pair.assertAccepts(Value.Enum("key"), Value.Enum("key2"))
    }
}
