package xyz.malefic.parz.idl

import kotlin.test.Test
import kotlin.test.assertFailsWith

class Str {
    @Test fun nonStr() = Type.Str().assertRejectsOtherKinds()

    @Test
    fun invalidStr() {
        assertFailsWith<IllegalArgumentException> { Type.Str(maxLen = 0) }
        assertFailsWith<IllegalArgumentException> { Type.Str(maxLen = -1) }
        Type.Str(maxLen = 10).assertRejects(Value.Str("hello world"))
        Type.Str(maxLen = 1).assertRejects(Value.Str("hello"))
    }

    @Test
    fun isStr() {
        Type.Str().assertAccepts(Value.Str(""), Value.Str("hello"))
        Type.Str(maxLen = 10).assertAccepts(Value.Str("hello"))
        Type.Str(maxLen = 1).assertAccepts(Value.Str(""), Value.Str("e"))
    }
}
