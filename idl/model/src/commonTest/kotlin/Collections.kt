package xyz.malefic.parz.idl.model

import xyz.malefic.parz.idl.model.Type.Collection.Option
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import kotlin.test.Test

/**
 * Conformance of values to option and sequence types.
 */
class Collections {
    private val bool = Option(Bool)
    private val nested = Option(bool)

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

    private val bools = Seq(Bool)

    @Test fun nonSeq() = bools.assertRejectsOtherKinds()

    @Test
    fun isSeq() {
        val t = Value.Bool(true)
        val f = Value.Bool(false)
        bools.assertAccepts(
            Value.Seq(listOf()),
            Value.Seq(listOf(t)),
            Value.Seq(listOf(f)),
            Value.Seq(listOf(t, t, t)),
            Value.Seq(listOf(f, f, f)),
        )
    }
}
