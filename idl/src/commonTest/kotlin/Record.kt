package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type.Record.Field
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Record {
    @Test
    fun nonRecord() {
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Void }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Bool(true) }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Decimal(BigInteger.ZERO, 0) }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Seq(emptyList()) }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Str("") }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Enum("") }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Some(Value.Void) }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.None }
    }

    @Test
    fun invalidRecord() {
        assertFails { Type.Record(emptyList()) accepts Value.Record(mapOf("key" to Value.Str("hello world"))) }
        assertFails {
            Type.Record(Field("key", Type.Str()), Field("key", Type.Bool)) accepts
                Value.Record(mapOf("key" to Value.Str("hello world")))
        }
        assertFalse { Type.Record(Field("key", Type.Str())) accepts Value.Record(emptyMap()) }
    }

    @Test
    fun isRecord() {
        assertTrue { Type.Record(Field("key", Type.Str())) accepts Value.Record(mapOf("key" to Value.Str("hello world"))) }
        assertTrue { Type.Record(Field("key", Type.Str())) accepts Value.Record(mapOf("key" to Value.Str("hello world"))) }
    }
}
