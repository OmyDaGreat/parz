package xyz.malefic.parz.idl.model

import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Str
import xyz.malefic.parz.idl.model.Type.Primitive.Void
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Conformance of values to the void, bool, and string types.
 */
class Primitives {
    @Test fun nonVoid() = Void.assertRejectsOtherKinds()

    @Test fun isVoid() = Void.assertAccepts(Value.Void)

    @Test fun nonBool() = Bool.assertRejectsOtherKinds()

    @Test fun isBool() = Bool.assertAccepts(Value.Bool(true), Value.Bool(false))

    @Test fun nonStr() = Str().assertRejectsOtherKinds()

    @Test
    fun invalidStr() {
        assertFailsWith<IllegalArgumentException> { Str(maxLen = 0) }
        assertFailsWith<IllegalArgumentException> { Str(maxLen = -1) }
        Str(maxLen = 10).assertRejects(Value.Str("hello world"))
        Str(maxLen = 1).assertRejects(Value.Str("hello"))
    }

    @Test
    fun isStr() {
        Str().assertAccepts(Value.Str(""), Value.Str("hello"))
        Str(maxLen = 10).assertAccepts(Value.Str("hello"))
        Str(maxLen = 1).assertAccepts(Value.Str(""), Value.Str("e"))
    }
}
