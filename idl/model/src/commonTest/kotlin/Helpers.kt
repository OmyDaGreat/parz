package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun dec(
    unscaled: Int,
    scale: Int = 0,
) = Value.Decimal(BigInteger(unscaled), scale)

fun dec(
    unscaled: BigInteger,
    scale: Int = 0,
) = Value.Decimal(unscaled, scale)

fun mismatch(reason: Reason) = Mismatch(listOf(), reason)

/**
 * One value of every kind, used to check that a type rejects kinds it does not model.
 */
private val samples =
    listOf(
        Value.Void,
        Value.Bool(true),
        dec(0),
        Value.Str(""),
        Value.Seq(emptyList()),
        Value.Record(emptyMap()),
        Value.Enum(""),
        Value.Some(Value.Void),
        Value.None,
    )

/**
 * A schema holding a single declaration, with a reference to it.
 */
data class Declared(
    val schema: Schema,
    val ref: Type.Ref,
)

fun record(vararg fields: Decl.Record.Field) = Declared(Schema(listOf(Decl.Record("Test", fields.toList()))), Type.Ref("Test"))

fun enum(vararg members: Decl.Enum.Member) = Declared(Schema(listOf(Decl.Enum("Test", members.toList()))), Type.Ref("Test"))

fun Type.assertAccepts(
    vararg values: Value,
    schema: Schema = Schema.EMPTY,
) = values.forEach { assertTrue(accepts(it, schema), "$this should accept $it") }

fun Type.assertRejects(
    vararg values: Value,
    schema: Schema = Schema.EMPTY,
) = values.forEach { assertFalse(accepts(it, schema), "$this should reject $it") }

fun Declared.assertAccepts(vararg values: Value) = ref.assertAccepts(*values, schema = schema)

fun Declared.assertRejects(vararg values: Value) = ref.assertRejects(*values, schema = schema)

/**
 * Assert that this type rejects a sample of every value kind not in [kinds].
 */
fun Type.assertRejectsOtherKinds(
    kinds: Set<String> = setOf(kind),
    schema: Schema = Schema.EMPTY,
) = assertRejects(*samples.filter { it.kind !in kinds }.toTypedArray(), schema = schema)

fun Declared.assertRejectsOtherKinds(kind: String) = ref.assertRejectsOtherKinds(setOf(kind), schema)
