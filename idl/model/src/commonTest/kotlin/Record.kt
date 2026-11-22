package xyz.malefic.parz.idl.model

import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Str
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Record {
    private val single = record(Field("key", Str()))
    private val hello = Value.Str("hello world")

    @Test fun nonRecord() = single.assertRejectsOtherKinds("record")

    @Test
    fun invalidRecord() {
        assertFailsWith<IllegalArgumentException> { record() }
        assertFailsWith<IllegalArgumentException> { record(Field("key", Str()), Field("key", Bool)) }
        single.assertRejects(Value.Record(emptyMap()))
    }

    @Test
    fun isRecord() {
        single.assertAccepts(Value.Record(mapOf("key" to hello)))
        record(Field("key", Str()), Field("key2", Str())).assertAccepts(Value.Record(mapOf("key" to hello, "key2" to hello)))
    }

    @Test
    fun fieldMismatches() {
        val (schema, ref) = single
        assertEquals(
            listOf(Mismatch(listOf(Segment.Field("key")), Reason.MissingField("key"))),
            ref.check(Value.Record(emptyMap()), schema),
        )
        assertEquals(
            listOf(Mismatch(listOf(Segment.Field("extra")), Reason.ExtraField("extra"))),
            ref.check(Value.Record(mapOf("key" to hello, "extra" to hello)), schema),
        )
        assertEquals(
            listOf(Mismatch(listOf(Segment.Field("key")), Reason.WrongKind("string", "bool"))),
            ref.check(Value.Record(mapOf("key" to Value.Bool(true))), schema),
        )
    }

    @Test
    fun wrongKindNamesRecord() {
        val (schema, ref) = single
        assertEquals(listOf(mismatch(Reason.WrongKind("record", "bool"))), ref.check(Value.Bool(true), schema))
    }

    @Test
    fun unresolvedRef() {
        assertFailsWith<IllegalStateException> { Type.Ref("Missing") check Value.Record(emptyMap()) }
    }
}
