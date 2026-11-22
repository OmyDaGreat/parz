package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Type.Primitive.Str
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Str {
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
