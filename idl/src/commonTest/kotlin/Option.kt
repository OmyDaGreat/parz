package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Option {
    @Test
    fun nonOption() {
        assertFalse { Type.Option(Type.Bool) accepts Value.Void }
        assertFalse { Type.Option(Type.Bool) accepts Value.Decimal(BigInteger.ZERO, 0) }
        assertFalse { Type.Option(Type.Bool) accepts Value.Str("") }
        assertFalse { Type.Option(Type.Bool) accepts Value.Seq(emptyList()) }
        assertFalse { Type.Option(Type.Bool) accepts Value.Record(emptyMap()) }
        assertFalse { Type.Option(Type.Bool) accepts Value.Enum("") }
        assertFalse { Type.Option(Type.Bool) accepts Value.Bool(true) }
        assertFalse { Type.Option(Type.Bool) accepts Value.Bool(false) }
    }

    @Test
    fun isOption() {
        assertTrue { Type.Option(Type.Bool) accepts Value.Some(Value.Bool(true)) }
        assertTrue { Type.Option(Type.Bool) accepts Value.Some(Value.Bool(false)) }
        assertTrue { Type.Option(Type.Bool) accepts Value.None }
        assertTrue { Type.Option(Type.Option(Type.Bool)) accepts Value.Some(Value.Some(Value.Bool(true))) }
        assertTrue { Type.Option(Type.Option(Type.Bool)) accepts Value.Some(Value.Some(Value.Bool(false))) }
        assertTrue { Type.Option(Type.Option(Type.Bool)) accepts Value.Some(Value.None) }
        assertTrue { Type.Option(Type.Option(Type.Bool)) accepts Value.None }
    }
}
