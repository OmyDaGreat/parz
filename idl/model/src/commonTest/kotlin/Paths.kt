package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mismatch paths through nested types.
 */
class Paths {
    private val schema =
        Schema(
            listOf(
                Decl.Record("Holder", listOf(Field("expr", Type.Ref("Expr")))),
                Decl.Variant(
                    "Expr",
                    listOf(
                        Member("lit", 0, listOf(Field("text", Type.Str()))),
                        Member("neg", 1, listOf(Field("inner", Type.Ref("Expr")))),
                    ),
                ),
            ),
        )

    private fun lit(text: Value) = Value.Variant("lit", mapOf("text" to text))

    private fun neg(inner: Value) = Value.Variant("neg", mapOf("inner" to inner))

    @Test
    fun optionInner() =
        assertEquals(
            listOf(Mismatch(listOf(Segment.Inner), Reason.WrongKind("bool", "string"))),
            Type.Option(Type.Bool) check Value.Some(Value.Str("x")),
        )

    @Test
    fun seqIndices() =
        assertEquals(
            listOf(
                Mismatch(listOf(Segment.Index(1)), Reason.WrongKind("bool", "string")),
                Mismatch(listOf(Segment.Index(3)), Reason.WrongKind("bool", "void")),
            ),
            Type.Seq(Type.Bool) check Value.Seq(listOf(Value.Bool(true), Value.Str(""), Value.Bool(false), Value.Void)),
        )

    @Test
    fun recordThroughVariants() =
        assertEquals(
            listOf(
                Mismatch(
                    listOf(
                        Segment.Field("expr"),
                        Segment.Member("neg"),
                        Segment.Field("inner"),
                        Segment.Member("lit"),
                        Segment.Field("text"),
                    ),
                    Reason.WrongKind("string", "bool"),
                ),
            ),
            Type.Ref("Holder").check(Value.Record(mapOf("expr" to neg(lit(Value.Bool(true))))), schema),
        )

    @Test
    fun seqOfRecords() =
        assertEquals(
            listOf(Mismatch(listOf(Segment.Index(1), Segment.Field("expr")), Reason.MissingField("expr"))),
            Type.Seq(Type.Ref("Holder")).check(
                Value.Seq(listOf(Value.Record(mapOf("expr" to lit(Value.Str("ok")))), Value.Record(emptyMap()))),
                schema,
            ),
        )

    @Test
    fun deepRecursionAccepted() = Type.Ref("Expr").assertAccepts(neg(neg(neg(lit(Value.Str("x"))))), schema = schema)
}
