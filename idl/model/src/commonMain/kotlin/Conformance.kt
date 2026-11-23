package xyz.malefic.parz.idl.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.model.Reason.DanglingId
import xyz.malefic.parz.idl.model.Reason.DuplicateTable
import xyz.malefic.parz.idl.model.Reason.ExtraField
import xyz.malefic.parz.idl.model.Reason.Fit.InexactLength
import xyz.malefic.parz.idl.model.Reason.Fit.InexactScale
import xyz.malefic.parz.idl.model.Reason.Fit.NegativeId
import xyz.malefic.parz.idl.model.Reason.Fit.OutOfRange
import xyz.malefic.parz.idl.model.Reason.Fit.TooLong
import xyz.malefic.parz.idl.model.Reason.Fit.TooShort
import xyz.malefic.parz.idl.model.Reason.MissingField
import xyz.malefic.parz.idl.model.Reason.MissingTable
import xyz.malefic.parz.idl.model.Reason.UnknownMember
import xyz.malefic.parz.idl.model.Reason.WrongKind
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Rounding.PROHIBITED

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
     * A field in a Record or Variant member payload.
     */
    data class Field(
        val name: String,
    ) : Segment

    /**
     * A member in a Variant.
     */
    data class Member(
        val name: String,
    ) : Segment

    /**
     * An index in a [Type.Collection.Seq] or [Type.Collection.Table].
     */
    data class Index(
        val i: Int,
    ) : Segment

    /**
     * A key in a [Type.Collection.Map].
     */
    data class Key(
        val key: Value,
    ) : Segment

    /**
     * The value stored under [key] in a [Type.Collection.Map].
     */
    data class Entry(
        val key: Value,
    ) : Segment

    /**
     * An inner value in a [Type.Collection.Option].
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

    /**
     * The id is at or past the end of its target's table, which has [size] entries.
     */
    data class DanglingId(
        val target: String,
        val id: Value.Id,
        val size: Int,
    ) : Reason

    /**
     * There's an id for [target] but no `table<target>` under the root value.
     */
    data class MissingTable(
        val target: String,
    ) : Reason

    /**
     * There's more than one `table<target>` under the root value, so ids for [target] would be ambiguous.
     */
    data class DuplicateTable(
        val target: String,
    ) : Reason

    /**
     * A reason for a mismatch that indicates the value does not fit within the type's constraints.
     */
    sealed interface Fit : Reason {
        /**
         * The id value is negative and should be non-negative.
         */
        data class NegativeId(
            val value: Int,
        ) : Fit

        /**
         * The value is out of the range of the type.
         */
        data class OutOfRange(
            val min: BigInteger,
            val max: BigInteger,
        ) : Fit

        /**
         * The value is longer than the maximum length of the type.
         */
        data class TooLong(
            val maxLen: Long,
            val actual: Long,
        ) : Fit

        /**
         * The value is shorter than the minimum length of the type.
         */
        data class TooShort(
            val minLen: Long,
            val actual: Long,
        ) : Fit

        /**
         * The value does not have the expected length from the type.
         */
        data class InexactLength(
            val exactLen: Long,
            val actual: Long,
        ) : Fit

        /**
         * The value has a different scale than the type.
         */
        data class InexactScale(
            val expected: Int,
            val actual: Int,
        ) : Fit
    }
}

/**
 * Report a mismatch at [path].
 */
context(sink: Sink<Mismatch>)
private fun mismatch(
    path: List<Segment>,
    reason: Reason,
) = sink.report(Mismatch(path, reason))

/**
 * Expect a value to be of a certain kind, reporting [WrongKind] at [path] otherwise.
 *
 * @param kind The expected kind of the type
 * @param v The value to be checked
 * @param f Called with the value if it has the expected kind
 */
context(_: Sink<Mismatch>)
private inline fun <reified V : Value> expect(
    kind: String,
    v: Value,
    path: List<Segment>,
    f: (V) -> Unit = {},
) = if (v is V) f(v) else mismatch(path, WrongKind(kind, v.kind))

/**
 * Check a value against a type, resolving any [Type.Ref] through [schema].
 *
 * Decimals are checked strictly (rounding and overflow are ignored).
 */
fun Type.check(
    v: Value,
    schema: Schema,
): List<Mismatch> = collecting { checkInto(v, schema) }.diagnostics

/**
 * Check a value against a type, reporting each mismatch to the sink as it's found, with paths starting from [path].
 */
context(_: Sink<Mismatch>)
fun Type.checkInto(
    v: Value,
    schema: Schema,
    path: List<Segment> = emptyList(),
) {
    when (this) {
        Type.Primitive.Void -> {
            expect<Value.Void>(kind, v, path)
        }

        Type.Primitive.Bool -> {
            expect<Value.Bool>(kind, v, path)
        }

        is Type.Primitive.Id -> {
            expect<Value.Id>(kind, v, path) {
                if (it.index < 0) mismatch(path, NegativeId(it.index))
            }
        }

        is Type.Primitive.Bytes -> {
            expect<Value.Bytes>(kind, v, path) {
                val size = it.size.toLong()
                if (exact) {
                    if (size != maxLen) mismatch(path, InexactLength(maxLen, size))
                } else {
                    if (size > maxLen) mismatch(path, TooLong(maxLen, size))
                    if (size < minLen) mismatch(path, TooShort(minLen, size))
                }
            }
        }

        is Type.Primitive.Str -> {
            expect<Value.Str>(kind, v, path) {
                if (it.v.length > maxLen) mismatch(path, TooLong(maxLen, it.v.length.toLong()))
            }
        }

        is Type.Primitive.Decimal -> {
            expect<Value.Decimal>(kind, v, path) { dec ->
                if (!dec.isExactAt(scale)) mismatch(path, InexactScale(scale, dec.scale))
                val common = maxOf(scale, dec.scale)
                val factor = BigInteger.TEN.pow(common - scale)
                if (dec.rescale(common, PROHIBITED).unscaled !in (min * factor)..(max * factor)) {
                    mismatch(path, OutOfRange(min, max))
                }
            }
        }

        is Type.Collection.Option -> {
            when (v) {
                is Value.None -> Unit
                is Value.Some -> inner.checkInto(v.v, schema, path + Segment.Inner)
                else -> mismatch(path, WrongKind(kind, v.kind))
            }
        }

        is Type.Collection.Seq -> {
            expect<Value.Seq>(kind, v, path) { seq -> checkItems(inner, seq, schema, path) }
        }

        is Type.Collection.Table -> {
            expect<Value.Seq>(kind, v, path) { seq -> checkItems(inner, seq, schema, path) }
        }

        is Type.Collection.Map -> {
            expect<Value.Map>(kind, v, path) { map ->
                map.entries.forEach { (key, value) ->
                    keys.checkInto(key, schema, path + Segment.Key(key))
                    values.checkInto(value, schema, path + Segment.Entry(key))
                }
            }
        }

        is Type.Ref -> {
            when (val resolved = schema.resolve(this)) {
                is Resolved.Record -> resolved.decl.checkInto(v, schema, path)
                is Resolved.Variant -> resolved.decl.checkInto(v, schema, path)
                is Resolved.Other -> resolved.type.checkInto(v, schema, path)
            }
        }
    }
}

context(_: Sink<Mismatch>)
private fun checkItems(
    inner: Type,
    seq: Value.Seq,
    schema: Schema,
    path: List<Segment>,
) = seq.items.forEachIndexed { i, item -> inner.checkInto(item, schema, path + Segment.Index(i)) }

/**
 * Check a value against a record declaration:
 * - Every field must be present
 * - No extra fields may be present
 * - Each field must match up
 */
context(_: Sink<Mismatch>)
private fun Decl.Record.checkInto(
    v: Value,
    schema: Schema,
    path: List<Segment>,
) = expect<Value.Record>("record", v, path) { checkFields(fields, it.fields, schema, path) }

/**
 * Check a value against a variant declaration:
 * - Its name must be one of the members
 * - Every field must be present
 * - No extra fields may be present
 * - Each field must match up
 */
context(_: Sink<Mismatch>)
private fun Decl.Variant.checkInto(
    v: Value,
    schema: Schema,
    path: List<Segment>,
) = expect<Value.Variant>("variant", v, path) { variant ->
    val member = members.find { it.name == variant.name }
    if (member == null) {
        mismatch(path, UnknownMember(variant.name))
    } else {
        checkFields(member.payload, variant.payload, schema, path + Segment.Member(member.name))
    }
}

context(_: Sink<Mismatch>)
private fun checkFields(
    declared: List<Decl.Record.Field>,
    actual: Value.Fields,
    schema: Schema,
    path: List<Segment>,
) {
    declared.forEach { field ->
        val fieldPath = path + Segment.Field(field.name)
        val value = actual[field.name]
        if (value != null) field.type.checkInto(value, schema, fieldPath) else mismatch(fieldPath, MissingField(field.name))
    }
    val names = declared.mapTo(HashSet()) { it.name }
    actual.keys.filter { it !in names }.forEach { mismatch(path + Segment.Field(it), ExtraField(it)) }
}

/**
 * Check that every `id<T>` inside [value] points at an entry of the root's single `table<T>`:
 * - Each target may have at most one table under the root ([DuplicateTable])
 * - Every id needs a table for its target ([MissingTable])
 * - Every id must be less than its table's size ([DanglingId])
 *
 * [value] must already conform to [root] (see [check]); ids are walked everywhere, including inside table entries and map keys.
 */
fun checkReferences(
    value: Value,
    root: Type,
    schema: Schema,
) = collecting {
    val sizes = mutableMapOf<String, Int>()
    walk(value, root, schema, emptyList()) { type, v, path ->
        if (type is Type.Collection.Table) {
            val target = (type.inner as? Type.Ref)?.name ?: return@walk
            if (target in sizes) mismatch(path, DuplicateTable(target)) else sizes[target] = (v as Value.Seq).items.size
        }
    }
    walk(value, root, schema, emptyList()) { type, v, path ->
        if (type is Type.Primitive.Id) {
            val size = sizes[type.target]
            when {
                size == null -> mismatch(path, MissingTable(type.target))
                (v as Value.Id).index >= size -> mismatch(path, DanglingId(type.target, v, size))
            }
        }
    }
}.diagnostics

/**
 * Visit every (type, value, path) inside a value that conforms to [type], parents before children.
 * Only the member a variant actually holds is walked.
 */
private fun walk(
    value: Value,
    type: Type,
    schema: Schema,
    path: List<Segment>,
    visit: (Type, Value, List<Segment>) -> Unit,
) {
    visit(type, value, path)
    when (type) {
        is Type.Primitive -> {}

        is Type.Collection.Option -> {
            if (value is Value.Some) walk(value.v, type.inner, schema, path + Segment.Inner, visit)
        }

        is Type.Collection.Seq -> {
            (value as Value.Seq).items.forEachIndexed { i, item -> walk(item, type.inner, schema, path + Segment.Index(i), visit) }
        }

        is Type.Collection.Table -> {
            (value as Value.Seq).items.forEachIndexed { i, item -> walk(item, type.inner, schema, path + Segment.Index(i), visit) }
        }

        is Type.Collection.Map -> {
            (value as Value.Map).entries.forEach { (k, v) ->
                walk(k, type.keys, schema, path + Segment.Key(k), visit)
                walk(v, type.values, schema, path + Segment.Entry(k), visit)
            }
        }

        is Type.Ref -> {
            when (val resolved = schema.resolve(type)) {
                is Resolved.Record -> {
                    val fields = (value as Value.Record).fields
                    resolved.decl.fields.forEach { walk(fields.getValue(it.name), it.type, schema, path + Segment.Field(it.name), visit) }
                }

                is Resolved.Variant -> {
                    val variant = value as Value.Variant
                    val member = resolved.decl.members.first { it.name == variant.name }
                    val memberPath = path + Segment.Member(member.name)
                    member.payload.forEach {
                        walk(variant.payload.getValue(it.name), it.type, schema, memberPath + Segment.Field(it.name), visit)
                    }
                }

                is Resolved.Other -> {
                    walk(value, resolved.type, schema, path, visit)
                }
            }
        }
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
