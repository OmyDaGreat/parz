package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Reason.ExtraField
import xyz.malefic.parz.idl.Reason.InexactScale
import xyz.malefic.parz.idl.Reason.MissingField
import xyz.malefic.parz.idl.Reason.OutOfRange
import xyz.malefic.parz.idl.Reason.TooLong
import xyz.malefic.parz.idl.Reason.UnknownMember
import xyz.malefic.parz.idl.Reason.WrongKind

data class Mismatch(
    val path: List<Segment>,
    val reason: Reason,
)

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

sealed interface Reason {
    data class WrongKind(
        val expected: String,
        val actual: String,
    ) : Reason

    data class OutOfRange(
        val min: BigInteger,
        val max: BigInteger,
    ) : Reason

    data class TooLong(
        val maxLen: Long,
        val actual: Long,
    ) : Reason

    data class InexactScale(
        val expected: Int,
        val actual: Int,
    ) : Reason

    data class UnknownMember(
        val name: String,
    ) : Reason

    data class MissingField(
        val name: String,
    ) : Reason

    data class ExtraField(
        val name: String,
    ) : Reason
}

private inline fun <reified V : Value> Type.expect(
    v: Value,
    f: context(MutableList<Mismatch>) (V) -> Unit = {},
): List<Mismatch> = if (v is V) buildList { f(v) } else wrongKind(v)

private fun Type.wrongKind(v: Value) = listOf(Mismatch(listOf(), WrongKind(this.kind, v.kind)))

context(m: MutableList<Mismatch>)
fun add(
    path: List<Segment>,
    reason: Reason,
) = m.add(Mismatch(path, reason))

fun Type.check(v: Value): List<Mismatch> =
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
            expect<Value.Dec>(v) {
                if (it.unscaled !in min..max) {
                    add(listOf(), OutOfRange(min, max))
                }
                if (it.scale != scale) {
                    add(listOf(), InexactScale(scale, it.scale))
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

fun Type.accepts(v: Value) = check(v).isEmpty()
