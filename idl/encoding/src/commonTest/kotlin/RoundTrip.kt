package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Encoding then decoding gives back the same value, and canonical text survives a decode and re-encode byte for byte.
 */
class RoundTrip {
    private val samples: List<Pair<Value, Type>> =
        listOf(
            Value.Void to Type.Void,
            Value.Bool(true) to Type.Bool,
            dec(-5, 2) to money,
            dec(0, 2) to money,
            dec(-100_000, 2) to money,
            dec(12, -1) to decimal(-100, 100, -1),
            strValue("") to Type.Str(),
            strValue("tab\there \"quoted\" \\ \u0000 \u001b café 😀 中文") to Type.Str(),
            strValue((0..0x7f).map { it.toChar() }.joinToString("")) to Type.Str(),
            Value.None to Type.Option(Type.Option(Type.Void)),
            some(Value.None) to Type.Option(Type.Option(Type.Void)),
            some(some(Value.Void)) to Type.Option(Type.Option(Type.Void)),
            Value.Seq(emptyList()) to Type.Seq(Type.Seq(Type.Bool)),
            Value.Seq(listOf(Value.Seq(emptyList()), Value.Seq(listOf(Value.Bool(false))))) to Type.Seq(Type.Seq(Type.Bool)),
            lineValue(1234, "note") to line,
            pending to status,
            failed("card", 255) to status,
            Value.Record(mapOf("lines" to Value.Seq(listOf(lineValue(100), lineValue(-250, "x"))), "status" to failed("", 0))) to order,
        )

    @Test fun valueSurvivesEncodeAndDecode() =
        samples.forEach { (value, type) -> assertEquals(value, decodeOk(encoded(value, type), type), "$value as $type") }

    @Test fun canonicalTextIsStable() =
        samples.forEach { (value, type) ->
            val text = encoded(value, type)
            assertEquals(text, encoded(decodeOk(text, type), type), "$value as $type")
        }

    @Test fun prettyOutputDecodesTheSame() =
        samples.forEach { (value, type) ->
            val tree = encode(value, type, shop)
            listOf(1, 20, 80).forEach { width ->
                assertEquals(value, decodeOk(tree.pretty(width), type), "width $width:\n${tree.pretty(width)}")
            }
        }

    @Test fun lenientInputBecomesCanonical() {
        listOf(
            "((note none) (amount 1.5))" to "((amount 1.50) (note none))",
            "( (amount 007) ; why\n (note none) )" to "((amount 7.00) (note none))",
            "((amount -0.000) (note none))" to "((amount 0.00) (note none))",
        ).forEach { (lenient, canonical) -> assertEquals(canonical, encoded(decodeOk(lenient, line), line)) }
    }
}
