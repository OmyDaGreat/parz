package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Reason.ExtraField
import xyz.malefic.parz.idl.Reason.InexactScale
import xyz.malefic.parz.idl.Reason.MissingField
import xyz.malefic.parz.idl.Reason.OutOfRange
import xyz.malefic.parz.idl.Reason.TooLong
import xyz.malefic.parz.idl.Reason.UnknownMember
import xyz.malefic.parz.idl.Reason.WrongKind
import xyz.malefic.parz.idl.Type.Decimal.Rounding.PROHIBITED

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
     * A field in a Record.
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
 *
 * @param kind The expected kind of the type ([wrongKind] is called otherwise)
 * @param v The value to be checked
 * @param f The function to be called if the value is of the expected type
 */
private inline fun <reified V : Value> expect(
    kind: String,
    v: Value,
    f: context(MutableList<Mismatch>) (V) -> Unit = {},
): List<Mismatch> = if (v is V) buildList { f(v) } else wrongKind(kind, v)

/**
 * Convenience method to return a mismatch of type wrong kind.
 */
private fun wrongKind(
    kind: String,
    v: Value,
) = listOf(Mismatch(listOf(), WrongKind(kind, v.kind)))

/**
 * Convenience method to add a mismatch to a list.
 */
@IgnorableReturnValue
context(m: MutableList<Mismatch>)
private fun add(
    path: List<Segment>,
    reason: Reason,
) = m.add(Mismatch(path, reason))

/**
 * Check a value against a type, resolving any [Type.Ref] through [schema].
 *
 * Decimals are checked strictly (rounding and overflow are ignored).
 */
fun Type.check(
    v: Value,
    schema: Schema,
): List<Mismatch> =
    when (this) {
        Type.Void -> {
            expect<Value.Void>(kind, v)
        }

        Type.Bool -> {
            expect<Value.Bool>(kind, v)
        }

        is Type.Str -> {
            expect<Value.Str>(kind, v) {
                if (it.v.length > this.maxLen) {
                    add(listOf(), TooLong(this.maxLen, it.v.length.toLong()))
                }
            }
        }

        is Type.Decimal -> {
            expect<Value.Decimal>(kind, v) { dec ->
                if (!dec.isExactAt(scale)) {
                    add(listOf(), InexactScale(scale, dec.scale))
                }
                val common = maxOf(scale, dec.scale)
                val factor = BigInteger.TEN.pow(common - scale)
                if (dec.rescale(common, PROHIBITED).unscaled !in (min * factor)..(max * factor)) {
                    add(listOf(), OutOfRange(min, max))
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
                        inner.check(v.v, schema).forEach {
                            add(listOf(Segment.Inner) + it.path, it.reason)
                        }
                    }
                }

                else -> {
                    wrongKind(kind, v)
                }
            }
        }

        is Type.Seq -> {
            expect<Value.Seq>(kind, v) { seq ->
                seq.items.forEachIndexed { i, item ->
                    inner.check(item, schema).forEach {
                        add(listOf(Segment.Index(i)) + it.path, it.reason)
                    }
                }
            }
        }

        is Type.Ref -> {
            when (val resolved = schema.resolve(this)) {
                is Resolved.Record -> resolved.decl.check(v, schema)
                is Resolved.Enum -> resolved.decl.check(v)
                is Resolved.Other -> resolved.type.check(v, schema)
            }
        }
    }

/**
 * Check a value against a record declaration:
 * - Every field must be present
 * - No extra fields may be present
 * - Each field must match up
 */
private fun Decl.Record.check(
    v: Value,
    schema: Schema,
): List<Mismatch> =
    expect<Value.Record>("record", v) { record ->
        fields.forEach { field ->
            val value = record.fields[field.name]
            if (value == null) {
                add(listOf(Segment.Field(field.name)), MissingField(field.name))
            } else {
                field.type.check(value, schema).forEach {
                    add(listOf(Segment.Field(field.name)) + it.path, it.reason)
                }
            }
        }
        val names = fields.map { it.name }
        record.fields.keys
            .filter { it !in names }
            .forEach { add(listOf(Segment.Field(it)), ExtraField(it)) }
    }

/**
 * Check a value against an enum declaration:
 * - Its name must be one of the members
 */
private fun Decl.Enum.check(v: Value): List<Mismatch> =
    expect<Value.Enum>("enum", v) { enum ->
        if (members.none { it.name == enum.name }) {
            add(listOf(), UnknownMember(enum.name))
        }
    }

/**
 * Check a value against a type with no named declarations to resolve against.
 */
infix fun Type.check(v: Value) = check(v, Schema.EMPTY)

/**
 * Convenience method to check a value against a type, ignoring the specifications of mismatches.
 */
fun Type.accepts(
    v: Value,
    schema: Schema,
) = check(v, schema).isEmpty()

/**
 * Convenience method to check a value against a type with no named declarations, ignoring the specifications of mismatches.
 */
infix fun Type.accepts(v: Value) = accepts(v, Schema.EMPTY)
