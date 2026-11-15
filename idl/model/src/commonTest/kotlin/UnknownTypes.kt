package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import xyz.malefic.parz.idl.SchemaErrorKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.SchemaErrorKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.SchemaErrorKind.UnknownType
import kotlin.test.Test
import kotlin.test.assertEquals

class UnknownTypes {
    private fun kinds(vararg decls: Decl) = validate(decls.toList()).diagnostics.map { it.kind }

    @Test
    fun inRecordField() {
        val field = Field("x", Type.Ref("Nope"))
        val decl = Decl.Record("A", listOf(field))
        val kinds = kinds(decl)
        assertEquals(listOf(UnknownType("Nope", decl, RecordPath(field))), kinds)
        assertEquals("Unknown type in field x: Nope", kinds.single().message)
    }

    @Test
    fun insideContainers() {
        val field = Field("x", Type.Seq(Type.Option(Type.Ref("Nope"))))
        val decl = Decl.Record("A", listOf(field))
        assertEquals(listOf(UnknownType("Nope", decl, RecordPath(field))), kinds(decl))
    }

    @Test
    fun onePerField() {
        val x = Field("x", Type.Ref("Nope"))
        val y = Field("y", Type.Ref("Nope"))
        val decl = Decl.Record("A", listOf(x, y))
        assertEquals(listOf(UnknownType("Nope", decl, RecordPath(x)), UnknownType("Nope", decl, RecordPath(y))), kinds(decl))
    }

    @Test
    fun inVariantMember() {
        val field = Field("x", Type.Ref("Nope"))
        val member = Member("lit", 0, listOf(field))
        val decl = Decl.Variant("V", listOf(member))
        val kinds = kinds(decl)
        assertEquals(listOf(UnknownType("Nope", decl, VariantPath(member, field))), kinds)
        assertEquals("Unknown type in field lit.x: Nope", kinds.single().message)
    }

    @Test
    fun inAlias() {
        val decl = Decl.Alias("A", Type.Option(Type.Ref("Nope")))
        val kinds = kinds(decl)
        assertEquals(listOf(UnknownType("Nope", decl, null)), kinds)
        assertEquals("Unknown type: Nope", kinds.single().message)
    }

    @Test
    fun caseSensitive() {
        val field = Field("x", Type.Ref("foo"))
        val decl = Decl.Record("A", listOf(field))
        assertEquals(listOf(UnknownType("foo", decl, RecordPath(field))), kinds(decl, Decl.Alias("Foo", Type.Bool)))
    }

    @Test
    fun knownTypesPass() {
        assertEquals(
            emptyList(),
            kinds(
                Decl.Record("A", listOf(Field("b", Type.Ref("B")))),
                Decl.Alias("B", Type.Seq(Type.Ref("C"))),
                Decl.Variant("C", listOf(Member("a", 0))),
            ),
        )
    }
}
