package xyz.malefic.parz.idl

import kotlin.test.Test

class Option {
    private val bool = Type.Option(Type.Bool)
    private val nested = Type.Option(bool)

    @Test
    fun nonOption() {
        bool.assertRejectsOtherKinds(setOf("some", "none"))
        bool.assertRejects(Value.Bool(true), Value.Bool(false))
    }

    @Test
    fun isOption() {
        bool.assertAccepts(Value.Some(Value.Bool(true)), Value.Some(Value.Bool(false)), Value.None)
        nested.assertAccepts(
            Value.Some(Value.Some(Value.Bool(true))),
            Value.Some(Value.Some(Value.Bool(false))),
            Value.Some(Value.None),
            Value.None,
        )
    }
}
