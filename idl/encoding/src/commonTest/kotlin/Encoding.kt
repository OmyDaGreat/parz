package xyz.malefic.parz.idl.encoding

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.model.Type.Collection.Option
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal
import xyz.malefic.parz.idl.model.Type.Primitive.Str
import xyz.malefic.parz.idl.model.Type.Primitive.Void
import xyz.malefic.parz.idl.model.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [encode] and [encodeToString]: the canonical text for each kind of value.
 */
class Encoding {
    @Test fun void() = assertEquals("()", encoded(Value.Void, Void))

    @Test fun bools() {
        assertEquals("true", encoded(Value.Bool(true), Bool))
        assertEquals("false", encoded(Value.Bool(false), Bool))
    }

    @Test fun decimalsAreWrittenAtTheTypeScale() {
        assertEquals("12.34", encoded(dec(1234, 2), money))
        assertEquals("-0.05", encoded(dec(-5, 2), money))
        assertEquals("0.50", encoded(dec(50, 2), money))
        assertEquals("1000.00", encoded(dec(100_000, 2), money))
    }

    @Test fun zeroHasNoSign() {
        assertEquals("0.00", encoded(dec(0, 2), money))
        assertEquals("0", encoded(dec(0), count))
    }

    @Test fun lowerScaleValuesArePadded() {
        assertEquals("1.50", encoded(dec(15, 1), money))
        assertEquals("7.00", encoded(dec(7), money))
    }

    @Test fun scaleZeroHasNoPoint() = assertEquals("255", encoded(dec(255), count))

    @Test fun negativeScaleWritesTrailingZeros() {
        val tens = decimal(-100, 100, -1)
        assertEquals("120", encoded(dec(12, -1), tens))
        assertEquals("-120", encoded(dec(-120), tens))
        assertEquals("0", encoded(dec(0, -1), tens))
    }

    @Test fun decimalsBeyondLong() {
        val huge = BigInteger.parseString("123456789012345678901234567890", 10)
        val type = Decimal(-huge, huge, 3)
        assertEquals("123456789012345678901234567.890", encoded(Value.Decimal(huge, 3), type))
    }

    @Test fun strings() {
        assertEquals("\"hello\"", encoded(strValue("hello"), Str()))
        assertEquals("\"\"", encoded(strValue(""), Str()))
        assertEquals("\"say \\\"hi\\\"\\n\"", encoded(strValue("say \"hi\"\n"), Str()))
    }

    @Test fun options() {
        val type = Option(Str())
        assertEquals("none", encoded(Value.None, type))
        assertEquals("(some \"x\")", encoded(some(strValue("x")), type))
    }

    @Test fun nestedOptions() {
        val type = Option(Option(Bool))
        assertEquals("none", encoded(Value.None, type))
        assertEquals("(some none)", encoded(some(Value.None), type))
        assertEquals("(some (some true))", encoded(some(some(Value.Bool(true))), type))
    }

    @Test fun seqs() {
        val type = Seq(money)
        assertEquals("()", encoded(Value.Seq(emptyList()), type))
        assertEquals("(1.00 -2.50)", encoded(Value.Seq(listOf(dec(100, 2), dec(-250, 2))), type))
    }

    @Test fun recordsUseDeclarationOrder() {
        val backwards = Value.Record(mapOf("note" to Value.None, "amount" to dec(1234, 2)))
        assertEquals("((amount 12.34) (note none))", encoded(backwards, line))
    }

    @Test fun memberWithoutFieldsIsABareAtom() = assertEquals("pending", encoded(pending, status))

    @Test fun memberWithFieldsUsesDeclarationOrder() {
        val backwards = Value.Variant("failed", mapOf("code" to dec(3), "why" to strValue("card")))
        assertEquals("(failed (why \"card\") (code 3))", encoded(backwards, status))
    }

    @Test fun aliasesEncodeAsTheirTarget() = assertEquals("-0.05", encoded(dec(-5, 2), amount))

    @Test fun nested() =
        assertEquals(
            "((lines (((amount 1.00) (note none)) ((amount 2.50) (note (some \"tip\"))))) (status (failed (why \"card\") (code 3))))",
            encoded(
                Value.Record(mapOf("lines" to Value.Seq(listOf(lineValue(100), lineValue(250, "tip"))), "status" to failed("card", 3))),
                order,
            ),
        )

    @Test fun encodeBuildsTreesWithoutSpans() =
        assertEquals(
            list(list(atom("amount"), atom("12.34")), list(atom("note"), list(atom("some"), str("x")))),
            encode(lineValue(1234, "x"), line, shop),
        )

    @Test fun nonConformingValuesAreRejected() {
        listOf(
            Value.Bool(true) to money,
            dec(1, 3) to money,
            dec(100_001, 2) to money,
            strValue("too long!") to Str(8),
            Value.Record(mapOf("amount" to dec(1, 2))) to line,
            Value.Variant("unknown") to status,
            Value.Seq(listOf(Value.Void)) to Seq(Bool),
        ).forEach { (value, type) ->
            assertFailsWith<IllegalArgumentException>("$value as $type") { encode(value, type, shop) }
        }
    }

    @Test fun deterministic() {
        val value = Value.Record(mapOf("lines" to Value.Seq(listOf(lineValue(100))), "status" to pending))
        assertEquals(encoded(value, order), encoded(value, order))
    }
}
