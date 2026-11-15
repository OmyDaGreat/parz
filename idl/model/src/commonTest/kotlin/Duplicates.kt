package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import xyz.malefic.parz.idl.SchemaErrorKind.DuplicateDecl
import xyz.malefic.parz.idl.SchemaErrorKind.DuplicateFieldName
import xyz.malefic.parz.idl.SchemaErrorKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.SchemaErrorKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberName
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberTag
import kotlin.test.Test
import kotlin.test.assertEquals

class Duplicates {
    private fun kinds(vararg decls: Decl) = validate(decls.toList()).diagnostics.map { it.kind }

    private val flag = Field("x", Type.Bool)

    @Test
    fun duplicateDecl() {
        val first = Decl.Record("A", listOf(flag))
        val second = Decl.Alias("A", Type.Bool)
        val kinds = kinds(first, second)
        assertEquals(listOf(DuplicateDecl(second)), kinds)
        assertEquals("Duplicate declaration: A", kinds.single().message)
    }

    @Test
    fun duplicateDeclIgnoresCaseAndUnderscores() {
        val second = Decl.Alias("foobar", Type.Bool)
        assertEquals(listOf(DuplicateDecl(second)), kinds(Decl.Alias("Foo_Bar", Type.Bool), second))
    }

    @Test
    fun duplicateDeclIsNotValidatedFurther() {
        val second = Decl.Alias("A", Type.Ref("Nope"))
        assertEquals(listOf(DuplicateDecl(second)), kinds(Decl.Alias("A", Type.Bool), second))
    }

    @Test
    fun duplicateRecordField() {
        val second = Field("my_field", Type.Str())
        val decl = Decl.Record("A", listOf(Field("myField", Type.Bool), second))
        val kinds = kinds(decl)
        assertEquals(listOf(DuplicateFieldName(RecordPath(second), decl)), kinds)
        assertEquals("Duplicate field my_field in A", kinds.single().message)
    }

    @Test
    fun duplicateMemberField() {
        val second = Field("x", Type.Str())
        val member = Member("m", 0, listOf(flag, second))
        val decl = Decl.Variant("V", listOf(member))
        assertEquals(listOf(DuplicateFieldName(VariantPath(member, second), decl)), kinds(decl))
    }

    @Test
    fun sameFieldInDifferentMembers() {
        assertEquals(emptyList(), kinds(Decl.Variant("V", listOf(Member("a", 0, listOf(flag)), Member("b", 1, listOf(flag))))))
    }

    @Test
    fun duplicateMemberName() {
        val second = Member("Pending", 1)
        val decl = Decl.Variant("V", listOf(Member("pending", 0), second))
        val kinds = kinds(decl)
        assertEquals(listOf(DuplicateMemberName("Pending", decl, second)), kinds)
        assertEquals("Duplicate member name: Pending", kinds.single().message)
    }

    @Test
    fun duplicateMemberTag() {
        val second = Member("b", 3)
        val decl = Decl.Variant("V", listOf(Member("a", 3), second))
        val kinds = kinds(decl)
        assertEquals(listOf(DuplicateMemberTag(3, decl, second)), kinds)
        assertEquals("Duplicate member tag: 3", kinds.single().message)
    }

    @Test
    fun nameAndTagBothDuplicated() {
        val second = Member("a", 0)
        val decl = Decl.Variant("V", listOf(Member("a", 0), second))
        assertEquals(listOf(DuplicateMemberName("a", decl, second), DuplicateMemberTag(0, decl, second)), kinds(decl))
    }
}
