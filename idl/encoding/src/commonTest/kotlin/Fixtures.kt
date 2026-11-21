package xyz.malefic.parz.idl.encoding

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Schema
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A decimal type holding unscaled values in [min]..[max] at [scale].
 */
fun decimal(
    min: Long,
    max: Long,
    scale: Int = 0,
) = Type.Primitive.Decimal(BigInteger(min), BigInteger(max), scale)

fun dec(
    unscaled: Long,
    scale: Int = 0,
) = Value.Decimal(BigInteger(unscaled), scale)

fun field(
    name: String,
    type: Type,
) = Decl.Record.Field(name, type)

fun some(v: Value) = Value.Some(v)

fun strValue(v: String) = Value.Str(v)

/** -1000.00..1000.00 */
val money = decimal(-100_000, 100_000, 2)

val count = decimal(0, 255)

val line = Type.Ref("Line")

val status = Type.Ref("Status")

val order = Type.Ref("Order")

val amount = Type.Ref("Amount")

/**
 * ```
 * alias Amount = Money-like decimal
 * record Line(amount: Amount, note: option<string>)
 * variant Status(pending, failed(why: string(8), code: u8))
 * record Order(lines: seq<Line>, status: Status)
 * ```
 */
val shop =
    Schema(
        listOf(
            Decl.Alias("Amount", money),
            Decl.Record("Line", listOf(field("amount", amount), field("note", Type.Collection.Option(Type.Primitive.Str())))),
            Decl.Variant(
                "Status",
                listOf(
                    Decl.Variant.Member("pending", 0),
                    Decl.Variant.Member("failed", 1, listOf(field("why", Type.Primitive.Str(8)), field("code", count))),
                ),
            ),
            Decl.Record("Order", listOf(field("lines", Type.Collection.Seq(line)), field("status", status))),
        ),
    )

fun lineValue(
    unscaled: Long,
    note: String? = null,
) = Value.Record(mapOf("amount" to dec(unscaled, 2), "note" to (note?.let { some(strValue(it)) } ?: Value.None)))

val pending = Value.Variant("pending")

fun failed(
    why: String,
    code: Long,
) = Value.Variant("failed", mapOf("why" to strValue(why), "code" to dec(code)))

fun encoded(
    value: Value,
    type: Type,
) = encodeToString(value, type, shop)

/**
 * Decode [text], failing the test if there are any errors.
 */
fun decodeOk(
    text: String,
    type: Type,
): Value {
    val (value, diagnostics) = decodeString(text, type, shop)
    assertEquals(emptyList(), diagnostics, "unexpected errors decoding:\n$text")
    return assertNotNull(value, "no value decoding:\n$text")
}

/**
 * Decode [text], which must fail; returns its diagnostics.
 */
fun decodeErrors(
    text: String,
    type: Type,
): List<AnyDiagnostic> {
    val (value, diagnostics) = decodeString(text, type, shop)
    assertEquals(null, value, "expected no value decoding:\n$text")
    return diagnostics
}

fun codes(diagnostics: List<AnyDiagnostic>) = diagnostics.map { it.kind.code }
