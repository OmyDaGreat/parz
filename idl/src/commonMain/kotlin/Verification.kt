package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Reason.ExtraField
import xyz.malefic.parz.idl.Reason.InexactScale
import xyz.malefic.parz.idl.Reason.MissingField
import xyz.malefic.parz.idl.Reason.OutOfRange
import xyz.malefic.parz.idl.Reason.TooLong
import xyz.malefic.parz.idl.Reason.UnknownMember
import xyz.malefic.parz.idl.Reason.WrongKind

/**
 * A mismatch between a type and a value.
 *
 * @param path the path to the mismatch
 * @param reason the reason for the mismatch
 */
data class Mismatch(
    val path: List<Segment>,
    val reason: Reason,
)

/**
 * A segment of a path, used to locate mismatches.
 */
sealed interface Segment {
    /**
     * A field in a [Type.Record].
     */
    data class Field(
        val name: String,
    ) : Segment

    /**
     * An index in a [Type.Seq].
     */
    data class Index(
        val i: Int,
    ) : Segment

    /**
     * An inner value in a [Type.Option].
     */
    data object Inner : Segment
}

/**
 * A reason for a mismatch.
 */
sealed interface Reason {
    /**
     * The value has a kind differing from the type.
     */
    data class WrongKind(
        val expected: String,
        val actual: String,
    ) : Reason

    /**
     * The value is out of the range of the type.
     */
    data class OutOfRange(
        val min: BigInteger,
        val max: BigInteger,
    ) : Reason

    /**
     * The value is longer than the maximum length of the type.
     */
    data class TooLong(
        val maxLen: Long,
        val actual: Long,
    ) : Reason

    /**
     * The value has a different scale than the type.
     */
    data class InexactScale(
        val expected: Int,
        val actual: Int,
    ) : Reason

    /**
     * The value has a member not reflected in the type.
     */
    data class UnknownMember(
        val name: String,
    ) : Reason

    /**
     * The value is missing a field from the type.
     */
    data class MissingField(
        val name: String,
    ) : Reason

    /**
     * The value has a field not reflected in the type.
     */
    data class ExtraField(
        val name: String,
    ) : Reason
}

/**
 * Expect a value to be of a certain type.
 */
private inline fun <reified V : Value> Type.expect(
    v: Value,
    f: context(MutableList<Mismatch>) (V) -> Unit = {},
): List<Mismatch> = if (v is V) buildList { f(v) } else wrongKind(v)

/**
 * Convenience method to return a mismatch of type wrong kind.
 */
private fun Type.wrongKind(v: Value) = listOf(Mismatch(listOf(), WrongKind(this.kind, v.kind)))

/**
 * Convenience method to add a mismatch to a list.
 */
context(m: MutableList<Mismatch>)
private fun add(
    path: List<Segment>,
    reason: Reason,
) = m.add(Mismatch(path, reason))

/**
 * Check a value against a type.
 */
infix fun Type.check(v: Value): List<Mismatch> =
    when (this) {
        Type.Void -> {
            expect<Value.Void>(v)
        }

        Type.Bool -> {
            expect<Value.Bool>(v)
        }

        is Type.Str -> {
            expect<Value.Str>(v) {
                if (it.v.length > this.maxLen) {
                    add(listOf(), TooLong(this.maxLen, it.v.length.toLong()))
                }
            }
        }

        is Type.Decimal -> {
            expect<Value.Dec>(v) { dec ->
                if (dec.scale == scale && dec.unscaled !in min..max) {
                    add(listOf(), OutOfRange(min, max))
                }
                if (dec.scale != scale) {
                    val newDec: Value.Dec
                    if (dec.scale > scale) {
                        val diff = dec.scale - scale
                        newDec = Value.Dec(dec.unscaled.shl(diff), scale)
                    } else {
                        val diff = scale - dec.scale
                        newDec = Value.Dec(dec.unscaled.shr(diff), scale)
                    }
                    if (newDec.unscaled !in min..max) {
                        add(listOf(), OutOfRange(min, max))
                    } else {
                        add(listOf(), InexactScale(scale, newDec.scale))
                    }
                }
            }
        }

        is Type.Enum -> {
            expect<Value.Enum>(v) { enum ->
                if (!members.map { it.name }.contains(enum.name)) {
                    add(listOf(), UnknownMember(enum.name))
                }
            }
        }

        is Type.Option -> {
            when (v) {
                is Value.None -> {
                    listOf()
                }

                is Value.Some -> {
                    buildList {
                        inner.check(v.v).forEach {
                            add(listOf(Segment.Inner) + it.path, it.reason)
                        }
                    }
                }

                else -> {
                    wrongKind(v)
                }
            }
        }

        is Type.Record -> {
            expect<Value.Record>(v) { record ->
                val combined = mutableMapOf<String, Pair<Type, Value>>()
                fields.forEach {
                    if (record.fields.containsKey(it.name)) {
                        combined[it.name] = it.type to record.fields[it.name]!!
                    } else {
                        add(listOf(Segment.Field(it.name)), MissingField(it.name))
                    }
                }
                record.fields.keys
                    .filter { !fields.names.contains(it) }
                    .forEach { add(listOf(Segment.Field(it)), ExtraField(it)) }
                combined
                    .forEach { field ->
                        val (type, value) = field.value
                        type.check(value).forEach { mismatch ->
                            add(listOf(Segment.Field(field.key)) + mismatch.path, mismatch.reason)
                        }
                    }
            }
        }

        is Type.Seq -> {
            expect<Value.Seq>(v) { seq ->
                seq.items.forEachIndexed { i, item ->
                    inner.check(item).forEach {
                        add(listOf(Segment.Index(i)) + it.path, it.reason)
                    }
                }
            }
        }
    }

/**
 * Convenience method to check a value against a type, ignoring the specifications of mismatches.
 */
infix fun Type.accepts(v: Value) = check(v).isEmpty()
