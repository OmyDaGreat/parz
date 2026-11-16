package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [decode] and [decodeString] on well-formed input, including the forms accepted beyond the canonical one.
 */
class Decoding {
    @Test fun void() = assertEquals(Value.Void, decodeOk("()", Type.Void))

    @Test fun bools() {
        assertEquals(Value.Bool(true), decodeOk("true", Type.Bool))
        assertEquals(Value.Bool(false), decodeOk("false", Type.Bool))
    }

    @Test fun decimalsComeBackAtTheTypeScale() {
        assertEquals(dec(1234, 2), decodeOk("12.34", money))
        assertEquals(dec(-5, 2), decodeOk("-0.05", money))
        assertEquals(dec(255), decodeOk("255", count))
    }

    @Test fun fewerDecimalPlacesAreAccepted() {
        assertEquals(dec(150, 2), decodeOk("1.5", money))
        assertEquals(dec(700, 2), decodeOk("7", money))
    }

    @Test fun trailingZerosBeyondTheScaleAreExact() = assertEquals(dec(150, 2), decodeOk("1.5000", money))

    @Test fun negativeScale() {
        val tens = decimal(-100, 100, -1)
        assertEquals(dec(12, -1), decodeOk("120", tens))
        assertEquals(dec(-12, -1), decodeOk("-120.0", tens))
    }

    @Test fun nonCanonicalDecimalsAreNormalised() {
        assertEquals("7.00", encoded(decodeOk("007", money), money))
        assertEquals("0.00", encoded(decodeOk("-0", money), money))
        assertEquals("0.00", encoded(decodeOk("-0.00", money), money))
    }

    @Test fun strings() {
        assertEquals(strValue("hello"), decodeOk("\"hello\"", Type.Str()))
        assertEquals(strValue("a\nb 😀"), decodeOk("\"a\\nb \\u{1F600}\"", Type.Str()))
        assertEquals(strValue("12345678"), decodeOk("\"12345678\"", Type.Str(8)))
    }

    @Test fun options() {
        val type = Type.Option(Type.Option(Type.Bool))
        assertEquals(Value.None, decodeOk("none", type))
        assertEquals(some(Value.None), decodeOk("(some none)", type))
        assertEquals(some(some(Value.Bool(false))), decodeOk("(some (some false))", type))
    }

    @Test fun seqs() {
        val type = Type.Seq(money)
        assertEquals(Value.Seq(emptyList()), decodeOk("()", type))
        assertEquals(Value.Seq(listOf(dec(100, 2), dec(-250, 2))), decodeOk("(1 -2.5)", type))
    }

    @Test fun records() = assertEquals(lineValue(1234, "x"), decodeOk("((amount 12.34) (note (some \"x\")))", line))

    @Test fun recordFieldsInAnyOrder() = assertEquals(lineValue(1234), decodeOk("((note none) (amount 12.34))", line))

    @Test fun membersWithoutFields() = assertEquals(pending, decodeOk("pending", status))

    @Test fun membersWithFields() = assertEquals(failed("card", 3), decodeOk("(failed (why \"card\") (code 3))", status))

    @Test fun memberFieldsInAnyOrder() = assertEquals(failed("card", 3), decodeOk("(failed (code 3) (why \"card\"))", status))

    @Test fun aliases() = assertEquals(dec(-5, 2), decodeOk("-0.05", amount))

    @Test fun nested() =
        assertEquals(
            Value.Record(mapOf("lines" to Value.Seq(listOf(lineValue(100), lineValue(250, "tip"))), "status" to failed("card", 3))),
            decodeOk(
                """
                ; an order
                ((status (failed (why "card") (code 3)))
                 (lines (((amount 1) (note none))
                         ((note (some "tip")) (amount 2.5)))))
                """.trimIndent(),
                order,
            ),
        )

    @Test fun decodeWorksOnTreesWithoutSpans() {
        val (value, diagnostics) =
            collecting<Value?, AnyDiagnostic> {
                decode(list(list(atom("amount"), atom("1")), list(atom("note"), atom("none"))), line, shop)
            }
        assertEquals(emptyList(), diagnostics)
        assertEquals(lineValue(100), value)
    }
}
