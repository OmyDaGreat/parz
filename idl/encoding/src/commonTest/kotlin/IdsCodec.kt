package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant.Member
import xyz.malefic.parz.idl.model.Mismatch
import xyz.malefic.parz.idl.model.Reason
import xyz.malefic.parz.idl.model.Schema
import xyz.malefic.parz.idl.model.Segment
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Collection.Map
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Collection.Table
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Id
import xyz.malefic.parz.idl.model.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * `id` as a bare non-negative integer, `table` written like `seq`, and reference checks in files (IDL223).
 */
class IdsCodec {
    private val schema =
        Schema(
            listOf(
                Decl.Variant("Stmt", listOf(Member("goTo", 0, listOf(Field("target", Id("Para")))), Member("stop", 1))),
                Decl.Record("Para", listOf(Field("body", Seq(Type.Ref("Stmt"))))),
                Decl.Record("Prog", listOf(Field("paras", Table(Type.Ref("Para"))), Field("entry", Id("Para")))),
            ),
        )

    private val schemaId = SchemaId("ir", 1)

    private val id = Id("Para")

    private val prog = Type.Ref("Prog")

    private fun goTo(n: Int) = Value.Variant("goTo", mapOf("target" to Value.Id(n)))

    private fun para(vararg stmts: Value) = Value.Record(mapOf("body" to Value.Seq(stmts.toList())))

    private fun prog(
        vararg paras: Value,
        entry: Int = 0,
    ) = Value.Record(mapOf("paras" to Value.Seq(paras.toList()), "entry" to Value.Id(entry)))

    private fun encodedWith(
        value: Value,
        type: Type,
    ) = encodeToString(value, type, schema)

    private fun decodedWith(
        text: String,
        type: Type,
    ) = decodeString(text, type, schema)

    @Test
    fun ids() {
        assertEquals("0", encodedWith(Value.Id(0), id))
        assertEquals("42", encodedWith(Value.Id(42), id))
        assertEquals(Value.Id(42), decodedWith("42", id).value)
        assertEquals(Value.Id(Int.MAX_VALUE), decodedWith("${Int.MAX_VALUE}", id).value)
    }

    @Test
    fun malformedIds() {
        listOf("-1", "+1", "01", "1.0", "1e3", "x", "\"1\"", "(1)", "${Int.MAX_VALUE + 1L}", "99999999999999999999").forEach { text ->
            val (value, diagnostics) = decodedWith(text, id)
            assertNull(value, text)
            assertEquals(listOf(CodecErrorKind.Expected("an id")), diagnostics.map { it.kind }, text)
            assertEquals(text.span(text), diagnostics.single().span, text)
        }
    }

    @Test
    fun negativeIdsCannotBeEncoded() {
        assertFailsWith<IllegalArgumentException> { encodedWith(Value.Id(-1), id) }
    }

    @Test
    fun tablesAreWrittenLikeSeqs() {
        val value = Value.Seq(listOf(para(goTo(1)), para(Value.Variant("stop"))))
        val text = encodedWith(value, Table(Type.Ref("Para")))
        assertEquals("(((body ((goTo (target 1))))) ((body (stop))))", text)
        assertEquals(value, decodedWith(text, Table(Type.Ref("Para"))).value)
    }

    @Test
    fun idMapKeysInNumericOrder() =
        assertEquals(
            "((2 true) (10 false) (100 true))",
            encodedWith(
                Value.Map(mapOf(Value.Id(100) to Value.Bool(true), Value.Id(2) to Value.Bool(true), Value.Id(10) to Value.Bool(false))),
                Map(id, Bool),
            ),
        )

    @Test
    fun fileRoundTrip() {
        val value = prog(para(goTo(1)), para(Value.Variant("stop"), goTo(0)), entry = 1)
        val text = encodeFile(value, prog, schema, schemaId)
        val (decoded, diagnostics) = decodeFile(text, schema, schemaId)
        assertEquals(emptyList(), diagnostics)
        assertEquals(value, decoded)
    }

    @Test
    fun encodeFileRejectsDanglingIds() {
        assertFailsWith<IllegalArgumentException> { encodeFile(prog(para(goTo(3))), prog, schema, schemaId) }
    }

    @Test
    fun decodeFileReportsDanglingIds() {
        val text = "(parz 1 (schema ir 1) Prog)\n((paras (((body ((goTo (target 3))))))) (entry 0))\n"
        val (value, diagnostics) = decodeFile(text, schema, schemaId)
        assertNull(value)
        val path =
            listOf(
                Segment.Field("paras"),
                Segment.Index(0),
                Segment.Field("body"),
                Segment.Index(0),
                Segment.Member("goTo"),
                Segment.Field("target"),
            )
        assertEquals(
            listOf(CodecErrorKind.BadReference(Mismatch(path, Reason.DanglingId("Para", Value.Id(3), 1)))),
            diagnostics.map { it.kind },
        )
        assertEquals("IDL223", diagnostics.single().kind.code)
    }

    @Test
    fun decodeFileReportsEveryBadReference() {
        val text = "(parz 1 (schema ir 1) Prog)\n((paras (((body ((goTo (target 3))))))) (entry 9))\n"
        assertEquals(listOf("IDL223", "IDL223"), codes(decodeFile(text, schema, schemaId).diagnostics))
    }

    @Test
    fun lenientDecodeKeepsTheValue() {
        val text = "(parz 1 (schema ir 1) Prog)\n((paras ()) (entry 0))\n"
        val (value, diagnostics) = decodeFileLenient(text, schema, schemaId)
        assertEquals(prog(entry = 0), value)
        assertEquals(listOf("IDL223"), codes(diagnostics))
    }

    @Test
    fun newCodes() {
        assertEquals("IDL222", CodecErrorKind.DuplicateKey(Value.Id(1)).code)
        assertEquals("IDL223", CodecErrorKind.BadReference(Mismatch(emptyList(), Reason.MissingTable("P"))).code)
    }
}
