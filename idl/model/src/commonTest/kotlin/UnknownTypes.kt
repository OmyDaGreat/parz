package xyz.malefic.parz.idl.model

import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant.Member
import xyz.malefic.parz.idl.model.SchemaIssueKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.model.SchemaIssueKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.model.SchemaIssueKind.UnknownType
import xyz.malefic.parz.idl.model.Type.Collection.Option
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Str
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
        val field = Field("x", Seq(Option(Type.Ref("Nope"))))
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
        val decl = Decl.Alias("A", Option(Type.Ref("Nope")))
        val kinds = kinds(decl)
        assertEquals(listOf(UnknownType("Nope", decl, null)), kinds)
        assertEquals("Unknown type: Nope", kinds.single().message)
    }

    @Test
    fun caseSensitive() {
        val field = Field("x", Type.Ref("foo"))
        val decl = Decl.Record("A", listOf(field))
        assertEquals(listOf(UnknownType("foo", decl, RecordPath(field))), kinds(decl, Decl.Alias("Foo", Bool)))
    }

    @Test
    fun knownTypesPass() {
        assertEquals(
            emptyList(),
            kinds(
                Decl.Record("A", listOf(Field("b", Type.Ref("B")))),
                Decl.Alias("B", Seq(Type.Ref("C"))),
                Decl.Variant("C", listOf(Member("a", 0))),
            ),
        )
    }

    @Test
    fun refs() {
        assertEquals(setOf("A"), Type.Ref("A").refs())
        assertEquals(setOf("A"), Seq(Option(Type.Ref("A"))).refs())
        assertEquals(emptySet(), Bool.refs())
        assertEquals(emptySet(), Seq(Str()).refs())
    }
}
