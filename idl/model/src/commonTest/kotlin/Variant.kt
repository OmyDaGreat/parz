package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Variant {
    private val single = variant(Member("key", 0))
    private val pair = variant(Member("key", 0), Member("key2", 1))
    private val expr =
        variant(
            Member("lit", 0, listOf(Field("text", Type.Str()), Field("exact", Type.Bool))),
            Member("none", 1),
        )
    private val hello = Value.Str("hello")

    @Test fun nonVariant() = single.assertRejectsOtherKinds("variant")

    @Test
    fun invalidVariant() {
        assertFailsWith<IllegalArgumentException> { variant() }
        assertFailsWith<IllegalArgumentException> { variant(Member("key", 1), Member("key", 0)) }
        assertFailsWith<IllegalArgumentException> { variant(Member("key1", 0), Member("key2", 0)) }
        assertFailsWith<IllegalArgumentException> { Member("", 0) }
        assertFailsWith<IllegalArgumentException> { Member("key", -1) }
        single.assertRejects(Value.Variant(""))
    }

    @Test
    fun isVariant() {
        single.assertAccepts(Value.Variant("key"))
        pair.assertAccepts(Value.Variant("key"), Value.Variant("key2"))
    }

    @Test
    fun unknownMember() {
        val (schema, ref) = pair
        assertEquals(listOf(mismatch(Reason.UnknownMember("key3"))), ref.check(Value.Variant("key3"), schema))
    }

    @Test
    fun memberWithFields() {
        expr.assertAccepts(
            Value.Variant("lit", mapOf("text" to hello, "exact" to Value.Bool(true))),
            Value.Variant("none"),
        )
    }

    @Test
    fun missingField() {
        val (schema, ref) = expr
        assertEquals(
            listOf(Mismatch(listOf(Segment.Member("lit"), Segment.Field("exact")), Reason.MissingField("exact"))),
            ref.check(Value.Variant("lit", mapOf("text" to hello)), schema),
        )
    }

    @Test
    fun extraField() {
        val (schema, ref) = expr
        assertEquals(
            listOf(Mismatch(listOf(Segment.Member("lit"), Segment.Field("extra")), Reason.ExtraField("extra"))),
            ref.check(Value.Variant("lit", mapOf("text" to hello, "exact" to Value.Bool(true), "extra" to hello)), schema),
        )
    }

    @Test
    fun wrongFieldType() {
        val (schema, ref) = expr
        assertEquals(
            listOf(Mismatch(listOf(Segment.Member("lit"), Segment.Field("text")), Reason.WrongKind("string", "bool"))),
            ref.check(Value.Variant("lit", mapOf("text" to Value.Bool(false), "exact" to Value.Bool(true))), schema),
        )
    }

    @Test
    fun payloadOnMemberWithoutFields() {
        val (schema, ref) = expr
        assertEquals(
            listOf(Mismatch(listOf(Segment.Member("none"), Segment.Field("text")), Reason.ExtraField("text"))),
            ref.check(Value.Variant("none", mapOf("text" to hello)), schema),
        )
    }

    @Test
    fun wrongKindNamesVariant() {
        val (schema, ref) = single
        assertEquals(listOf(mismatch(Reason.WrongKind("variant", "record"))), ref.check(Value.Record(emptyMap()), schema))
    }

    @Test
    fun enumRejectsFields() {
        assertFailsWith<IllegalArgumentException> {
            Decl.Variant("E", listOf(Member("a", 0, listOf(Field("x", Type.Bool)))), isEnum = true)
        }
    }

    @Test
    fun enumChecksLikeVariant() {
        val schema = Schema(listOf(Decl.Variant("Status", listOf(Member("pending", 0), Member("settled", 5)), isEnum = true)))
        Type.Ref("Status").assertAccepts(Value.Variant("pending"), Value.Variant("settled"), schema = schema)
        Type.Ref("Status").assertRejects(Value.Variant("closed"), schema = schema)
    }
}
