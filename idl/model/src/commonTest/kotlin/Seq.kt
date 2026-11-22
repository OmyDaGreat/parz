package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Type.Collection.Seq
import xyz.malefic.parz.idl.Type.Primitive.Bool
import kotlin.test.Test

class Seq {
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
