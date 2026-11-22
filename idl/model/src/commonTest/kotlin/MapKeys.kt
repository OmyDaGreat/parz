package xyz.malefic.parz.idl.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant.Member
import xyz.malefic.parz.idl.model.SchemaErrorKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.model.SchemaErrorKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.model.SchemaErrorKind.InvalidMapKey
import xyz.malefic.parz.idl.model.Type.Collection.Map
import xyz.malefic.parz.idl.model.Type.Collection.Option
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal
import xyz.malefic.parz.idl.model.Type.Primitive.Str
import xyz.malefic.parz.idl.model.Type.Primitive.Void
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [InvalidMapKey]: map keys must be bool, decimal, string, or an enum-like variant, directly or through aliases.
 */
class MapKeys {
    private fun kinds(vararg decls: Decl) = validate(decls.toList()).diagnostics.map { it.kind }

    private val count = Decimal(BigInteger.ZERO, BigInteger(255), 0)

    private val status = Decl.Variant("Status", listOf(Member("pending", 0), Member("done", 1)))

    private val result = Decl.Variant("Result", listOf(Member("ok", 0), Member("err", 1, listOf(Field("why", Str())))))

    private val point = Decl.Record("Point", listOf(Field("x", count)))

    /** A record `A` with one field `m` of type `map<[key], bool>`, plus [others]. */
    private fun keyedBy(
        key: Type,
        vararg others: Decl,
    ): List<SchemaErrorKind> {
        val field = Field("m", Map(key, Bool))
        val decl = Decl.Record("A", listOf(field))
        return kinds(decl, *others)
    }

    private fun assertAllowed(
        key: Type,
        vararg others: Decl,
    ) = assertEquals(emptyList(), keyedBy(key, *others), "$key should be an allowed key")

    private fun assertRejected(
        key: Type,
        vararg others: Decl,
    ) {
        val kinds = keyedBy(key, *others)
        assertEquals(1, kinds.size, "$key should be rejected once, got $kinds")
        assertEquals(key, (kinds.single() as InvalidMapKey).key)
    }

    @Test
    fun primitivesButVoidAreAllowed() {
        assertAllowed(Bool)
        assertAllowed(count)
        assertAllowed(Str())
        assertAllowed(Str(8))
        assertRejected(Void)
    }

    @Test
    fun collectionsAreRejected() {
        assertRejected(Option(Str()))
        assertRejected(Seq(Bool))
        assertRejected(Map(Str(), Bool))
    }

    @Test
    fun variantsWithoutPayloadsAreAllowed() {
        assertAllowed(Type.Ref("Status"), status)
        assertAllowed(Type.Ref("Status"), status.copy(isEnum = true))
    }

    @Test
    fun variantsWithAnyPayloadAreRejected() = assertRejected(Type.Ref("Result"), result)

    @Test
    fun recordsAreRejected() = assertRejected(Type.Ref("Point"), point)

    @Test
    fun aliasesAreFollowed() {
        assertAllowed(Type.Ref("Name"), Decl.Alias("Name", Str()))
        assertAllowed(Type.Ref("Key"), Decl.Alias("Key", Type.Ref("Status")), status)
        assertAllowed(Type.Ref("Outer"), Decl.Alias("Outer", Type.Ref("Inner")), Decl.Alias("Inner", Str()))
        assertRejected(Type.Ref("Points"), Decl.Alias("Points", Seq(Type.Ref("Point"))), point)
        assertRejected(Type.Ref("Outer"), Decl.Alias("Outer", Type.Ref("Inner")), Decl.Alias("Inner", Void))
    }

    @Test
    fun unknownKeyIsOnlyReportedAsUnknown() = assertEquals(listOf("IDL101"), keyedBy(Type.Ref("Nope")).map { it.code })

    @Test
    fun cyclicAliasKeyIsOnlyReportedAsACycle() {
        val codes = keyedBy(Type.Ref("X"), Decl.Alias("X", Type.Ref("Y")), Decl.Alias("Y", Type.Ref("X"))).map { it.code }
        assertTrue("IDL107" !in codes, "got $codes")
        assertTrue("IDL105" in codes, "got $codes")
    }

    @Test
    fun nestedMapsAreChecked() {
        val inSeq = Field("m", Seq(Map(Void, Bool)))
        val inOption = Field("n", Option(Map(Seq(Bool), Bool)))
        val inValue = Field("o", Map(Str(), Map(Void, Bool)))
        val inKey = Field("p", Map(Map(Void, Bool), Bool))
        val decl = Decl.Record("A", listOf(inSeq, inOption, inValue, inKey))
        assertEquals(
            listOf(
                InvalidMapKey(Void, decl, RecordPath(inSeq)),
                InvalidMapKey(Seq(Bool), decl, RecordPath(inOption)),
                InvalidMapKey(Void, decl, RecordPath(inValue)),
                InvalidMapKey(Map(Void, Bool), decl, RecordPath(inKey)),
                InvalidMapKey(Void, decl, RecordPath(inKey)),
            ),
            kinds(decl),
        )
    }

    @Test
    fun inVariantMember() {
        val field = Field("m", Map(Void, Bool))
        val member = Member("lit", 0, listOf(field))
        val decl = Decl.Variant("V", listOf(member))
        assertEquals(listOf(InvalidMapKey(Void, decl, VariantPath(member, field))), kinds(decl))
    }

    @Test
    fun inAlias() {
        val decl = Decl.Alias("T", Map(Void, Bool))
        assertEquals(listOf(InvalidMapKey(Void, decl, null)), kinds(decl))
    }

    @Test
    fun valuesCanBeAnything() {
        val decl = Decl.Record("A", listOf(Field("m", Map(Str(), Seq(Type.Ref("Point"))))))
        assertEquals(emptyList(), kinds(decl, point))
    }

    @Test
    fun messages() {
        val field = Field("m", Map(Type.Ref("Point"), Bool))
        val decl = Decl.Record("A", listOf(field))
        assertEquals(
            "Invalid map key in field m: Point (keys must be bool, decimal, string, or an enum)",
            InvalidMapKey(Type.Ref("Point"), decl, RecordPath(field)).message,
        )
        assertEquals(
            "Invalid map key: void (keys must be bool, decimal, string, or an enum)",
            InvalidMapKey(Void, Decl.Alias("T", Map(Void, Bool)), null).message,
        )
    }

    @Test
    fun code() = assertEquals("IDL107", InvalidMapKey(Void, Decl.Alias("T", Map(Void, Bool)), null).code)
}
