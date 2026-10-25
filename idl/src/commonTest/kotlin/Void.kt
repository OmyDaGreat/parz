import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.accepts
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Void {
    @Test
    fun nonVoid() {
        assertFalse { Type.Void.accepts(Value.Bool(true)) }
        assertFalse { Type.Void.accepts(Value.Dec(BigInteger.ZERO, 0)) }
        assertFalse { Type.Void.accepts(Value.Str("")) }
        assertFalse { Type.Void.accepts(Value.Seq(emptyList())) }
        assertFalse { Type.Void.accepts(Value.Record(emptyMap())) }
        assertFalse { Type.Void.accepts(Value.Enum("")) }
        assertFalse { Type.Void.accepts(Value.Some(Value.Void)) }
        assertFalse { Type.Void.accepts(Value.None) }
    }

    @Test
    fun isVoid() {
        assertTrue { Type.Void.accepts(Value.Void) }
    }
}
