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

fun Type.assertAccepts(vararg values: Value) = values.forEach { assertTrue(this accepts it, "$this should accept $it") }

fun Type.assertRejects(vararg values: Value) = values.forEach { assertFalse(this accepts it, "$this should reject $it") }

/**
 * Assert that this type rejects a sample of every value kind not in [kinds].
 */
fun Type.assertRejectsOtherKinds(kinds: Set<String> = setOf(kind)) = assertRejects(*samples.filter { it.kind !in kinds }.toTypedArray())
