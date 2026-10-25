package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Reason.*

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

fun Type.check(v: Value): List<Mismatch> =
    mutableListOf<Mismatch>()
        .apply {
            when (v) {
                Value.Empty -> {
                    when (this@check) {
                        Type.Empty -> Unit
                        Type.Bool -> add(Mismatch(listOf(), WrongKind("bool", "empty")))
                        is Type.Decimal -> add(Mismatch(listOf(), WrongKind("decimal", "empty")))
                        is Type.Enum -> add(Mismatch(listOf(), WrongKind("enum", "empty")))
                        is Type.Option -> add(Mismatch(listOf(), WrongKind("option", "empty")))
                        is Type.Record -> add(Mismatch(listOf(), WrongKind("record", "empty")))
                        is Type.Seq -> add(Mismatch(listOf(), WrongKind("seq", "empty")))
                        is Type.Str -> add(Mismatch(listOf(), WrongKind("string", "empty")))
                    }
                }

                is Value.Bool -> {
                    when (this@check) {
                        Type.Empty -> add(Mismatch(listOf(), WrongKind("empty", "bool")))
                        Type.Bool -> Unit
                        is Type.Decimal -> add(Mismatch(listOf(), WrongKind("decimal", "bool")))
                        is Type.Enum -> add(Mismatch(listOf(), WrongKind("enum", "bool")))
                        is Type.Option -> add(Mismatch(listOf(), WrongKind("option", "bool")))
                        is Type.Record -> add(Mismatch(listOf(), WrongKind("record", "bool")))
                        is Type.Seq -> add(Mismatch(listOf(), WrongKind("seq", "bool")))
                        is Type.Str -> add(Mismatch(listOf(), WrongKind("string", "bool")))
                    }
                }

                is Value.Str -> {
                    when (this@check) {
                        Type.Empty -> {
                            add(Mismatch(listOf(), WrongKind("empty", "some")))
                        }

                        Type.Bool -> {
                            add(Mismatch(listOf(), WrongKind("bool", "some")))
                        }

                        is Type.Decimal -> {
                            add(Mismatch(listOf(), WrongKind("decimal", "some")))
                        }

                        is Type.Enum -> {
                            add(Mismatch(listOf(), WrongKind("enum", "some")))
                        }

                        is Type.Option -> {
                            add(Mismatch(listOf(), WrongKind("option", "some")))
                        }

                        is Type.Record -> {
                            add(Mismatch(listOf(), WrongKind("record", "some")))
                        }

                        is Type.Seq -> {
                            add(Mismatch(listOf(), WrongKind("seq", "some")))
                        }

                        is Type.Str -> {
                            if (v.v.length > this@check.maxLen) {
                                add(Mismatch(listOf(), TooLong(this@check.maxLen, v.v.length.toLong())))
                            }
                        }
                    }
                }

                is Value.Dec -> {
                    when (this@check) {
                        Type.Empty -> {
                            add(Mismatch(listOf(), WrongKind("empty", "decimal")))
                        }

                        Type.Bool -> {
                            add(Mismatch(listOf(), WrongKind("bool", "decimal")))
                        }

                        is Type.Decimal -> {
                            if (v.unscaled < this@check.min || v.unscaled > this@check.max) {
                                add(Mismatch(listOf(), OutOfRange(this@check.min, this@check.max)))
                            }
                            if (v.scale != this@check.scale) {
                                add(Mismatch(listOf(), InexactScale(this@check.scale, v.scale)))
                            }
                        }

                        is Type.Enum -> {
                            add(Mismatch(listOf(), WrongKind("enum", "decimal")))
                        }

                        is Type.Option -> {
                            add(Mismatch(listOf(), WrongKind("option", "decimal")))
                        }

                        is Type.Record -> {
                            add(Mismatch(listOf(), WrongKind("record", "decimal")))
                        }

                        is Type.Seq -> {
                            add(Mismatch(listOf(), WrongKind("seq", "decimal")))
                        }

                        is Type.Str -> {
                            add(Mismatch(listOf(), WrongKind("string", "decimal")))
                        }
                    }
                }

                Value.None -> {
                    when (this@check) {
                        Type.Empty -> add(Mismatch(listOf(), WrongKind("empty", "none")))
                        Type.Bool -> add(Mismatch(listOf(), WrongKind("bool", "none")))
                        is Type.Decimal -> add(Mismatch(listOf(), WrongKind("decimal", "none")))
                        is Type.Enum -> add(Mismatch(listOf(), WrongKind("enum", "none")))
                        is Type.Option -> Unit
                        is Type.Record -> add(Mismatch(listOf(), WrongKind("record", "none")))
                        is Type.Seq -> add(Mismatch(listOf(), WrongKind("seq", "none")))
                        is Type.Str -> add(Mismatch(listOf(), WrongKind("string", "none")))
                    }
                }

                is Value.Some -> {
                    when (this@check) {
                        Type.Empty -> {
                            add(Mismatch(listOf(), WrongKind("empty", "some")))
                        }

                        Type.Bool -> {
                            add(Mismatch(listOf(), WrongKind("bool", "some")))
                        }

                        is Type.Decimal -> {
                            add(Mismatch(listOf(), WrongKind("decimal", "some")))
                        }

                        is Type.Enum -> {
                            add(Mismatch(listOf(), WrongKind("enum", "some")))
                        }

                        is Type.Option -> {
                            if (!this@check.inner.accepts(v.v)) {
                                add(Mismatch(listOf(Segment.Inner), WrongKind("option", "some")))
                            }
                        }

                        is Type.Record -> {
                            add(Mismatch(listOf(), WrongKind("record", "some")))
                        }

                        is Type.Seq -> {
                            add(Mismatch(listOf(), WrongKind("seq", "some")))
                        }

                        is Type.Str -> {
                            add(Mismatch(listOf(), WrongKind("string", "some")))
                        }
                    }
                }

                is Value.Enum -> {
                    when (this@check) {
                        Type.Empty -> {
                            add(Mismatch(listOf(), WrongKind("empty", "enum")))
                        }

                        Type.Bool -> {
                            add(Mismatch(listOf(), WrongKind("bool", "enum")))
                        }

                        is Type.Decimal -> {
                            add(Mismatch(listOf(), WrongKind("decimal", "enum")))
                        }

                        is Type.Enum -> {
                            if (!this@check.members.map { it.name }.contains(v.name)) {
                                add(Mismatch(listOf(), UnknownMember(v.name)))
                            }
                        }

                        is Type.Option -> {
                            add(Mismatch(listOf(), WrongKind("option", "enum")))
                        }

                        is Type.Record -> {
                            add(Mismatch(listOf(), WrongKind("record", "enum")))
                        }

                        is Type.Seq -> {
                            add(Mismatch(listOf(), WrongKind("seq", "enum")))
                        }

                        is Type.Str -> {
                            add(Mismatch(listOf(), WrongKind("string", "enum")))
                        }
                    }
                }

                is Value.Seq -> {
                    when (this@check) {
                        Type.Empty -> {
                            add(Mismatch(listOf(), WrongKind("empty", "seq")))
                        }

                        Type.Bool -> {
                            add(Mismatch(listOf(), WrongKind("bool", "seq")))
                        }

                        is Type.Decimal -> {
                            add(Mismatch(listOf(), WrongKind("decimal", "seq")))
                        }

                        is Type.Enum -> {
                            add(Mismatch(listOf(), WrongKind("enum", "seq")))
                        }

                        is Type.Option -> {
                            add(Mismatch(listOf(), WrongKind("option", "seq")))
                        }

                        is Type.Record -> {
                            add(Mismatch(listOf(), WrongKind("record", "seq")))
                        }

                        is Type.Seq -> {
                            v.items.forEach {
                                addAll(
                                    this@check.inner.check(it).map { mismatch ->
                                        Mismatch(listOf(Segment.Index(v.items.indexOf(it))) + mismatch.path, mismatch.reason)
                                    },
                                )
                            }
                        }

                        is Type.Str -> {
                            add(Mismatch(listOf(), WrongKind("string", "seq")))
                        }
                    }
                }

                is Value.Record -> {
                    when (this@check) {
                        Type.Empty -> {
                            add(Mismatch(listOf(), WrongKind("empty", "record")))
                        }

                        Type.Bool -> {
                            add(Mismatch(listOf(), WrongKind("bool", "record")))
                        }

                        is Type.Decimal -> {
                            add(Mismatch(listOf(), WrongKind("decimal", "record")))
                        }

                        is Type.Enum -> {
                            add(Mismatch(listOf(), WrongKind("enum", "record")))
                        }

                        is Type.Option -> {
                            add(Mismatch(listOf(), WrongKind("option", "record")))
                        }

                        is Type.Record -> {
                            val fields = mutableMapOf<String, Pair<Type, Value>>()
                            this@check.fields.forEach {
                                if (v.fields.containsKey(it.name)) {
                                    fields[it.name] = it.type to v.fields[it.name]!!
                                } else {
                                    add(Mismatch(listOf(Segment.Field(it.name)), MissingField(it.name)))
                                }
                            }
                            if (!v.fields.keys.containsAll(this@check.fields.names)) {
                                add(Mismatch(listOf(), MissingField(this@check.fields.names.first { !v.fields.keys.contains(it) })))
                            }
                            if (!this@check.fields.names.containsAll(v.fields.keys)) {
                                add(Mismatch(listOf(), ExtraField(v.fields.keys.first { !this@check.fields.names.contains(it) })))
                            }
                            fields
                                .forEach { field ->
                                    val (type, value) = field.value
                                    type.check(value).forEach { mismatch ->
                                        add(Mismatch(listOf(Segment.Field(field.key)) + mismatch.path, mismatch.reason))
                                    }
                                }
                        }

                        is Type.Seq -> {
                            add(Mismatch(listOf(), WrongKind("seq", "record")))
                        }

                        is Type.Str -> {
                            add(Mismatch(listOf(), WrongKind("string", "record")))
                        }
                    }
                }
            }
        }

fun Type.accepts(v: Value) = check(v).isEmpty()
