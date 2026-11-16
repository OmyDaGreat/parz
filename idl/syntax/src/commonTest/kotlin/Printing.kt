package xyz.malefic.parz.idl.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [PrettySyntax.pretty] output, and that it parses back to the same declarations.
 */
class Printing {
    private fun assertRoundTrips(source: String) {
        val first = parsed(source)
        val printed = first.pretty()
        val (second, errors) = parseSource(printed)
        assertEquals(emptyList(), errors, "pretty output does not parse:\n$printed")
        assertEquals(lower(first).value, lower(second).value, "pretty output means something else:\n$printed")
    }

    @Test fun alias() = assertEquals("type Payments = seq<Payment>", parsed("type Payments = seq<Payment>").pretty())

    @Test fun singleFieldRecord() = assertEquals("record A {\n  x: option<B>,\n}", parsed("record A { x: option<B> }").pretty())

    @Test fun enum() = assertEquals("enum E {\n  a = 0,\n}", parsed("enum E { a }").pretty())

    @Test fun declsSeparatedByBlankLine() = assertEquals("type A = bool\n\ntype B = void", parsed("type A = bool type B = void").pretty())

    @Test fun roundTripAlias() = assertRoundTrips("type A = seq<option<B>>")

    @Test fun roundTripSingleFieldRecord() = assertRoundTrips("record A { x: bool }")

    @Test fun roundTripRecord() = assertRoundTrips("record A { x: bool, y: B }")

    @Test fun roundTripVariant() = assertRoundTrips("variant V { a(x: bool, y: B), b = 7 }")

    @Test fun roundTripEnum() = assertRoundTrips("enum E { a, b = 5 }")

    @Test fun roundTripString() = assertRoundTrips("type A = string type B = string(10)")

    @Test fun roundTripDecimal() = assertRoundTrips("type A = decimal(9, 2, round_half_to_even, saturate)")

    @Test fun roundTripDecimalRange() = assertRoundTrips("type A = decimal(-5..10, 1)")
}
