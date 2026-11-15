package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Looking up and resolving declarations in a [Schema].
 */
class Resolution {
    private val record = Decl.Record("R", listOf(Field("x", Type.Bool)))
    private val variant = Decl.Variant("V", listOf(Member("a", 0)))
    private val schema =
        Schema(
            listOf(
                record,
                variant,
                Decl.Alias("ToR", Type.Ref("R")),
                Decl.Alias("ToToR", Type.Ref("ToR")),
                Decl.Alias("Flag", Type.Bool),
            ),
        )

    @Test
    fun get() {
        assertEquals(record, schema["R"])
        assertNull(schema["Missing"])
    }

    @Test
    fun resolveDecls() {
        assertEquals(Resolved.Record(record), schema.resolve(Type.Ref("R")))
        assertEquals(Resolved.Variant(variant), schema.resolve(Type.Ref("V")))
    }

    @Test
    fun resolveThroughAliases() {
        assertEquals(Resolved.Record(record), schema.resolve(Type.Ref("ToToR")))
        assertEquals(Resolved.Other(Type.Bool), schema.resolve(Type.Ref("Flag")))
    }

    @Test
    fun resolveNonRef() = assertEquals(Resolved.Other(Type.Seq(Type.Bool)), schema.resolve(Type.Seq(Type.Bool)))

    @Test
    fun resolveUnknown() {
        assertFailsWith<IllegalStateException> { schema.resolve(Type.Ref("Missing")) }
    }

    @Test
    fun invalidSchemaThrows() {
        assertFailsWith<IllegalArgumentException> { Schema(listOf(Decl.Alias("A", Type.Ref("Missing")))) }
    }

    @Test
    fun emptySchema() {
        assertTrue(Schema.EMPTY.decls.isEmpty())
        assertTrue(Schema.EMPTY.validate().ok)
    }

    @Test
    fun validateMatchesFreeFunction() = assertEquals(validate(schema.decls), schema.validate())
}
