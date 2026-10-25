import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.accepts
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Seq {
    @Test
    fun nonSeq() {
        assertFalse { Type.Seq(Type.Bool) accepts Value.Void }
        assertFalse { Type.Seq(Type.Bool) accepts Value.Dec(BigInteger.ZERO, 0) }
        assertFalse { Type.Seq(Type.Bool) accepts Value.Str("") }
        assertFalse { Type.Seq(Type.Bool) accepts Value.Record(emptyMap()) }
        assertFalse { Type.Seq(Type.Bool) accepts Value.Enum("") }
        assertFalse { Type.Seq(Type.Bool) accepts Value.Some(Value.Bool(true)) }
        assertFalse { Type.Seq(Type.Bool) accepts Value.Bool(false) }
        assertFalse { Type.Seq(Type.Bool) accepts Value.None }
    }

    @Test
    fun isSeq() {
        assertTrue { Type.Seq(Type.Bool) accepts Value.Seq(listOf()) }
        assertTrue { Type.Seq(Type.Bool) accepts Value.Seq(listOf()) }
        assertTrue { Type.Seq(Type.Bool) accepts Value.Seq(listOf(Value.Bool(true))) }
        assertTrue { Type.Seq(Type.Bool) accepts Value.Seq(listOf(Value.Bool(false))) }
        assertTrue { Type.Seq(Type.Bool) accepts Value.Seq(listOf(Value.Bool(true), Value.Bool(true), Value.Bool(true))) }
        assertTrue { Type.Seq(Type.Bool) accepts Value.Seq(listOf(Value.Bool(false), Value.Bool(false), Value.Bool(false))) }
    }
}
