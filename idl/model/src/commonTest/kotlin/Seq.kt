package xyz.malefic.parz.idl

import kotlin.test.Test

class Seq {
    private val bools = Type.Seq(Type.Bool)

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
