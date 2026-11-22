package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.model.Schema
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Collection.Map
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Str
import xyz.malefic.parz.idl.model.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Map encoding: `((key value) ...)`, with keys in typed canonical order ([keyOrder]).
 */
class Maps {
    /** Members declared out of tag order, so declaration order, name order, and tag order all differ. */
    private val schema =
        Schema(
            listOf(
                Decl.Variant(
                    "Level",
                    listOf(Decl.Variant.Member("low", 5), Decl.Variant.Member("high", 0), Decl.Variant.Member("mid", 3)),
                    isEnum = true,
                ),
                Decl.Alias("Name", Str()),
            ),
        )

    private fun mapOf(vararg entries: Pair<Value, Value>) = Value.Map(entries.toMap())

    private fun encodedMap(
        value: Value,
        type: Type,
    ) = encodeToString(value, type, schema)

    /** The keys of [value] in the order they're encoded, decoded back to values. */
    private fun keyOrderOf(
        value: Value.Map,
        type: Map,
    ): List<Value> =
        (encode(value, type, schema) as Sexp.List).items.map { entry ->
            val (key, diagnostics) = collecting { decode((entry as Sexp.List).items[0], type.keys, schema) }
            assertEquals(emptyList(), diagnostics)
            key!!
        }

    private val yes = Value.Bool(true)

    @Test fun empty() = assertEquals("()", encodedMap(mapOf(), Map(Str(), Bool)))

    @Test fun entriesArePairs() = assertEquals("((\"a\" true))", encodedMap(mapOf(strValue("a") to yes), Map(Str(), Bool)))

    @Test fun boolsFalseFirst() =
        assertEquals("((false 1) (true 0))", encodedMap(mapOf(yes to dec(0), Value.Bool(false) to dec(1)), Map(Bool, count)))

    @Test fun decimalsByNumericValue() {
        val keys = listOf(dec(1000, 2), dec(900, 2), dec(-5, 2), dec(0, 2), dec(-1000, 2), dec(-150, 2))
        val map = Value.Map(keys.associateWith { yes })
        assertEquals(
            listOf(dec(-1000, 2), dec(-150, 2), dec(-5, 2), dec(0, 2), dec(900, 2), dec(1000, 2)),
            keyOrderOf(map, Map(money, Bool)),
        )
    }

    @Test fun decimalsAtALowerScaleSortByValue() {
        val map = mapOf(dec(2) to yes, dec(15, 1) to yes, dec(101, 2) to yes)
        assertEquals(listOf(dec(101, 2), dec(150, 2), dec(200, 2)), keyOrderOf(map, Map(money, Bool)))
    }

    @Test fun decimalsBeyondLong() {
        val huge =
            com.ionspin.kotlin.bignum.integer.BigInteger
                .parseString("99999999999999999999999", 10)
        val type = Type.Primitive.Decimal(-huge, huge, 0)
        val map = mapOf(Value.Decimal(huge, 0) to yes, Value.Decimal(-huge, 0) to yes, dec(0) to yes)
        assertEquals(listOf(Value.Decimal(-huge, 0), dec(0), Value.Decimal(huge, 0)), keyOrderOf(map, Map(type, Bool)))
    }

    @Test fun stringsByCodePoint() {
        // B (U+42) < a (U+61) < b < é (U+E9) < 中 (U+4E2D) < � (U+FFFD) < 😀 (U+1F600).
        // UTF-16 order would put 😀 (surrogates start at U+D83D) before �.
        val keys = listOf("😀", "�", "b", "中", "a", "é", "B", "", "ab")
        val map = Value.Map(keys.associate { strValue(it) to yes })
        assertEquals(
            listOf("", "B", "a", "ab", "b", "é", "中", "�", "😀").map(::strValue),
            keyOrderOf(map, Map(Str(), Bool)),
        )
    }

    @Test fun prefixesSortFirst() =
        assertEquals(
            "((\"a\" true) (\"aa\" true) (\"ab\" true))",
            encodedMap(mapOf(strValue("ab") to yes, strValue("aa") to yes, strValue("a") to yes), Map(Str(), Bool)),
        )

    @Test fun enumsByTag() {
        val type = Map(Type.Ref("Level"), Bool)
        val map = mapOf(Value.Variant("low") to yes, Value.Variant("mid") to yes, Value.Variant("high") to yes)
        assertEquals("((high true) (mid true) (low true))", encodedMap(map, type))
    }

    @Test fun aliasesUseTheirTargetsOrder() {
        val map = mapOf(strValue("b") to yes, strValue("😀") to yes, strValue("�") to yes)
        assertEquals(listOf("b", "�", "😀").map(::strValue), keyOrderOf(map, Map(Type.Ref("Name"), Bool)))
    }

    @Test fun insertionOrderDoesNotMatter() {
        val type = Map(Str(), Bool)
        val pairs = listOf("x", "a", "m", "😀", "é").map { strValue(it) to yes }
        assertEquals(encodedMap(Value.Map(pairs.toMap()), type), encodedMap(Value.Map(pairs.reversed().toMap()), type))
    }

    @Test fun keysEqualAtTheTypeScaleAreDuplicates() {
        assertFailsWith<IllegalArgumentException> { encode(mapOf(dec(15, 1) to yes, dec(150, 2) to yes), Map(money, Bool), schema) }
    }

    @Test fun decodingAcceptsAnyOrderAndReencodesCanonically() {
        val type = Map(Type.Ref("Level"), Bool)
        val (value, diagnostics) = decodeString("((low true) (high false) (mid true))", type, schema)
        assertEquals(emptyList(), diagnostics)
        assertEquals("((high false) (mid true) (low true))", encodedMap(value!!, type))
    }
}
