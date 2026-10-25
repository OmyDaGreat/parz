import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.accepts
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bool {
    @Test
    fun nonBool() {
        assertFalse { Type.Bool accepts Value.Void }
        assertFalse { Type.Bool accepts Value.Dec(BigInteger.ZERO, 0) }
        assertFalse { Type.Bool accepts Value.Str("") }
        assertFalse { Type.Bool accepts Value.Seq(emptyList()) }
        assertFalse { Type.Bool accepts Value.Record(emptyMap()) }
        assertFalse { Type.Bool accepts Value.Enum("") }
        assertFalse { Type.Bool accepts Value.Some(Value.Bool(true)) }
        assertFalse { Type.Bool accepts Value.Some(Value.Bool(false)) }
        assertFalse { Type.Bool accepts Value.None }
    }

    @Test
    fun isBool() {
        assertTrue { Type.Bool accepts Value.Bool(true) }
        assertTrue { Type.Bool accepts Value.Bool(false) }
    }
}
