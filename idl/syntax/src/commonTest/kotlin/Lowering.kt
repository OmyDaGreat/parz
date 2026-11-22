package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyRecord
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyVariant
import xyz.malefic.parz.idl.ShapeErrorKind.EnumPayload
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Collection.Option
import xyz.malefic.parz.idl.Type.Collection.Seq
import xyz.malefic.parz.idl.Type.Primitive.Bool
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Turning syntax into model declarations with [DeclSyntax.toDecl].
 */
class Lowering {
    @Test
    fun record() {
        val result = lowered("record A { x: bool, y: seq<B> }")
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(listOf(Decl.Record("A", listOf(Field("x", Bool), Field("y", Seq(Type.Ref("B")))))), result.value)
    }

    @Test
    fun variant() {
        val result = lowered("variant V { a(x: bool), b = 4 }")
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(listOf(Decl.Variant("V", listOf(Member("a", 0, listOf(Field("x", Bool))), Member("b", 4)))), result.value)
    }

    @Test
    fun enum() =
        assertEquals(listOf(Decl.Variant("E", listOf(Member("a", 0), Member("b", 1)), isEnum = true)), lowered("enum E { a, b }").value)

    @Test
    fun alias() = assertEquals(listOf(Decl.Alias("T", Option(Bool))), lowered("type T = option<bool>").value)

    @Test
    fun emptyRecord() {
        val source = "record A {}"
        val result = lowered(source)
        assertEquals(listOf(null), result.value)
        assertEquals(listOf(Diagnostic(EmptyRecord, source.span("record A"))), result.diagnostics)
    }

    @Test
    fun emptyVariant() {
        val source = "enum E {}"
        val result = lowered(source)
        assertEquals(listOf(null), result.value)
        assertEquals(listOf(Diagnostic(EmptyVariant, source.span("enum E"))), result.diagnostics)
    }

    @Test
    fun enumWithFields() {
        val source = "enum E { b, a(x: bool) }"
        val result = lowered(source)
        assertEquals(listOf(null), result.value)
        assertEquals(
            listOf(Diagnostic(EnumPayload(Member("a", 1, listOf(Field("x", Bool)))), source.span("a"))),
            result.diagnostics,
        )
    }

    @Test
    fun badDeclDoesNotStopOthers() = assertEquals(listOf(null, Decl.Alias("T", Bool)), lowered("record A {}\ntype T = bool").value)
}
