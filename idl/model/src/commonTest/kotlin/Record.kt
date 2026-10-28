package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Type.Record.Field
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Record {
    private val single = Type.Record(Field("key", Type.Str()))
    private val hello = Value.Str("hello world")

    @Test fun nonRecord() = single.assertRejectsOtherKinds()

    @Test
    fun invalidRecord() {
        assertFailsWith<IllegalArgumentException> { Type.Record(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Type.Record(Field("key", Type.Str()), Field("key", Type.Bool)) }
        single.assertRejects(Value.Record(emptyMap()))
    }

    @Test
    fun isRecord() {
        single.assertAccepts(Value.Record(mapOf("key" to hello)))
        Type
            .Record(Field("key", Type.Str()), Field("key2", Type.Str()))
            .assertAccepts(Value.Record(mapOf("key" to hello, "key2" to hello)))
    }
}
