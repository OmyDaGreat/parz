import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.accepts
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Str {
    @Test
    fun nonStr() {
        assertFalse { Type.Str() accepts Value.Void }
        assertFalse { Type.Str() accepts Value.Bool(true) }
        assertFalse { Type.Str() accepts Value.Dec(BigInteger.ZERO, 0) }
        assertFalse { Type.Str() accepts Value.Seq(emptyList()) }
        assertFalse { Type.Str() accepts Value.Record(emptyMap()) }
        assertFalse { Type.Str() accepts Value.Enum("") }
        assertFalse { Type.Str() accepts Value.Some(Value.Void) }
        assertFalse { Type.Str() accepts Value.None }
    }

    @Test
    fun invalidStr() {
        assertFalse { Type.Str(maxLen = 10) accepts Value.Str("hello world") }
        assertFalse { Type.Str(maxLen = 1) accepts Value.Str("hello") }
        assertFails { Type.Str(maxLen = 0) accepts Value.Str("") }
        assertFails { Type.Str(maxLen = -1) accepts Value.Str("") }
    }

    @Test
    fun isStr() {
        assertTrue { Type.Str() accepts Value.Str("") }
        assertTrue { Type.Str() accepts Value.Str("hello") }
        assertTrue { Type.Str(maxLen = 10) accepts Value.Str("hello") }
        assertTrue { Type.Str(maxLen = 1) accepts Value.Str("") }
        assertTrue { Type.Str(maxLen = 1) accepts Value.Str("e") }
    }
}
