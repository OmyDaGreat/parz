package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ONE
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import xyz.malefic.parz.idl.Type.Enum.Member
import xyz.malefic.parz.idl.Type.Record.Field
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Enum {
    @Test
    fun nonEnum() {
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Void }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Bool(true) }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Decimal(ZERO, 0) }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Seq(emptyList()) }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Str("") }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Enum("") }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Record(emptyMap()) }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Some(Value.Void) }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.None }
    }

    @Test
    fun invalidEnum() {
        assertFails { Type.Enum(emptyList()) accepts Value.Enum("") }
        assertFails { Type.Enum(Member("key", ONE), Member("key", ZERO)) accepts Value.Enum("key") }
        assertFails { Type.Enum(Member("key1", ZERO), Member("key2", ZERO)) accepts Value.Enum("key2") }
        assertFalse { Type.Enum(Member("key", ZERO)) accepts Value.Enum("") }
    }

    @Test
    fun isEnum() {
        assertTrue { Type.Enum(Member("key", ZERO)) accepts Value.Enum("key") }
        assertTrue { Type.Enum(Member("key", ZERO), Member("key2", ONE)) accepts Value.Enum("key") }
        assertTrue { Type.Enum(Member("key", ZERO), Member("key2", ONE)) accepts Value.Enum("key2") }
    }
}
