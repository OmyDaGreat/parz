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
    context(mutableListOf<Mismatch>()) {
        fun add(
            path: List<Segment>,
            reason: Reason,
        ) = contextOf<MutableList<Mismatch>>().add(Mismatch(path, reason))

        when (v) {
            Value.Empty -> {
                when (this) {
                    Type.Empty -> Unit
                    Type.Bool -> add(listOf(), WrongKind("bool", "empty"))
                    is Type.Decimal -> add(listOf(), WrongKind("decimal", "empty"))
                    is Type.Enum -> add(listOf(), WrongKind("enum", "empty"))
                    is Type.Option -> add(listOf(), WrongKind("option", "empty"))
                    is Type.Record -> add(listOf(), WrongKind("record", "empty"))
                    is Type.Seq -> add(listOf(), WrongKind("seq", "empty"))
                    is Type.Str -> add(listOf(), WrongKind("string", "empty"))
                }
            }

            is Value.Bool -> {
                when (this) {
                    Type.Empty -> add(listOf(), WrongKind("empty", "bool"))
                    Type.Bool -> Unit
                    is Type.Decimal -> add(listOf(), WrongKind("decimal", "bool"))
                    is Type.Enum -> add(listOf(), WrongKind("enum", "bool"))
                    is Type.Option -> add(listOf(), WrongKind("option", "bool"))
                    is Type.Record -> add(listOf(), WrongKind("record", "bool"))
                    is Type.Seq -> add(listOf(), WrongKind("seq", "bool"))
                    is Type.Str -> add(listOf(), WrongKind("string", "bool"))
                }
            }

            is Value.Str -> {
                when (this) {
                    Type.Empty -> {
                        add(listOf(), WrongKind("empty", "string"))
                    }

                    Type.Bool -> {
                        add(listOf(), WrongKind("bool", "string"))
                    }

                    is Type.Decimal -> {
                        add(listOf(), WrongKind("decimal", "string"))
                    }

                    is Type.Enum -> {
                        add(listOf(), WrongKind("enum", "string"))
                    }

                    is Type.Option -> {
                        add(listOf(), WrongKind("option", "string"))
                    }

                    is Type.Record -> {
                        add(listOf(), WrongKind("record", "string"))
                    }

                    is Type.Seq -> {
                        add(listOf(), WrongKind("seq", "string"))
                    }

                    is Type.Str -> {
                        if (v.v.length > this.maxLen) {
                            add(listOf(), TooLong(this.maxLen, v.v.length.toLong()))
                        }
                    }
                }
            }

            is Value.Dec -> {
                when (this) {
                    Type.Empty -> {
                        add(listOf(), WrongKind("empty", "decimal"))
                    }

                    Type.Bool -> {
                        add(listOf(), WrongKind("bool", "decimal"))
                    }

                    is Type.Decimal -> {
                        if (v.unscaled !in min..max) {
                            add(listOf(), OutOfRange(min, max))
                        }
                        if (v.scale != scale) {
                            add(listOf(), InexactScale(scale, v.scale))
                        }
                    }

                    is Type.Enum -> {
                        add(listOf(), WrongKind("enum", "decimal"))
                    }

                    is Type.Option -> {
                        add(listOf(), WrongKind("option", "decimal"))
                    }

                    is Type.Record -> {
                        add(listOf(), WrongKind("record", "decimal"))
                    }

                    is Type.Seq -> {
                        add(listOf(), WrongKind("seq", "decimal"))
                    }

                    is Type.Str -> {
                        add(listOf(), WrongKind("string", "decimal"))
                    }
                }
            }

            Value.None -> {
                when (this) {
                    Type.Empty -> add(listOf(), WrongKind("empty", "none"))
                    Type.Bool -> add(listOf(), WrongKind("bool", "none"))
                    is Type.Decimal -> add(listOf(), WrongKind("decimal", "none"))
                    is Type.Enum -> add(listOf(), WrongKind("enum", "none"))
                    is Type.Option -> Unit
                    is Type.Record -> add(listOf(), WrongKind("record", "none"))
                    is Type.Seq -> add(listOf(), WrongKind("seq", "none"))
                    is Type.Str -> add(listOf(), WrongKind("string", "none"))
                }
            }

            is Value.Some -> {
                when (this) {
                    Type.Empty -> {
                        add(listOf(), WrongKind("empty", "some"))
                    }

                    Type.Bool -> {
                        add(listOf(), WrongKind("bool", "some"))
                    }

                    is Type.Decimal -> {
                        add(listOf(), WrongKind("decimal", "some"))
                    }

                    is Type.Enum -> {
                        add(listOf(), WrongKind("enum", "some"))
                    }

                    is Type.Option -> {
                        inner.check(v.v).forEach {
                            add(listOf(Segment.Inner) + it.path, it.reason)
                        }
                    }

                    is Type.Record -> {
                        add(listOf(), WrongKind("record", "some"))
                    }

                    is Type.Seq -> {
                        add(listOf(), WrongKind("seq", "some"))
                    }

                    is Type.Str -> {
                        add(listOf(), WrongKind("string", "some"))
                    }
                }
            }

            is Value.Enum -> {
                when (this) {
                    Type.Empty -> {
                        add(listOf(), WrongKind("empty", "enum"))
                    }

                    Type.Bool -> {
                        add(listOf(), WrongKind("bool", "enum"))
                    }

                    is Type.Decimal -> {
                        add(listOf(), WrongKind("decimal", "enum"))
                    }

                    is Type.Enum -> {
                        if (!members.map { it.name }.contains(v.name)) {
                            add(listOf(), UnknownMember(v.name))
                        }
                    }

                    is Type.Option -> {
                        add(listOf(), WrongKind("option", "enum"))
                    }

                    is Type.Record -> {
                        add(listOf(), WrongKind("record", "enum"))
                    }

                    is Type.Seq -> {
                        add(listOf(), WrongKind("seq", "enum"))
                    }

                    is Type.Str -> {
                        add(listOf(), WrongKind("string", "enum"))
                    }
                }
            }

            is Value.Seq -> {
                when (this) {
                    Type.Empty -> {
                        add(listOf(), WrongKind("empty", "seq"))
                    }

                    Type.Bool -> {
                        add(listOf(), WrongKind("bool", "seq"))
                    }

                    is Type.Decimal -> {
                        add(listOf(), WrongKind("decimal", "seq"))
                    }

                    is Type.Enum -> {
                        add(listOf(), WrongKind("enum", "seq"))
                    }

                    is Type.Option -> {
                        add(listOf(), WrongKind("option", "seq"))
                    }

                    is Type.Record -> {
                        add(listOf(), WrongKind("record", "seq"))
                    }

                    is Type.Seq -> {
                        v.items.forEach {
                            inner.check(it).forEachIndexed { index, mismatch ->
                                add(listOf(Segment.Index(index)) + mismatch.path, mismatch.reason)
                            }
                        }
                    }

                    is Type.Str -> {
                        add(listOf(), WrongKind("string", "seq"))
                    }
                }
            }

            is Value.Record -> {
                when (this) {
                    Type.Empty -> {
                        add(listOf(), WrongKind("empty", "record"))
                    }

                    Type.Bool -> {
                        add(listOf(), WrongKind("bool", "record"))
                    }

                    is Type.Decimal -> {
                        add(listOf(), WrongKind("decimal", "record"))
                    }

                    is Type.Enum -> {
                        add(listOf(), WrongKind("enum", "record"))
                    }

                    is Type.Option -> {
                        add(listOf(), WrongKind("option", "record"))
                    }

                    is Type.Record -> {
                        val combined = mutableMapOf<String, Pair<Type, Value>>()
                        fields.forEach {
                            if (v.fields.containsKey(it.name)) {
                                combined[it.name] = it.type to v.fields[it.name]!!
                            } else {
                                add(listOf(Segment.Field(it.name)), MissingField(it.name))
                            }
                        }
                        if (!v.fields.keys.containsAll(fields.names)) {
                            add(listOf(), MissingField(fields.names.first { !v.fields.keys.contains(it) }))
                        }
                        if (!fields.names.containsAll(v.fields.keys)) {
                            add(listOf(), ExtraField(v.fields.keys.first { !fields.names.contains(it) }))
                        }
                        combined
                            .forEach { field ->
                                val (type, value) = field.value
                                type.check(value).forEach { mismatch ->
                                    add(listOf(Segment.Field(field.key)) + mismatch.path, mismatch.reason)
                                }
                            }
                    }

                    is Type.Seq -> {
                        add(listOf(), WrongKind("seq", "record"))
                    }

                    is Type.Str -> {
                        add(listOf(), WrongKind("string", "record"))
                    }
                }
            }
        }
        contextOf<MutableList<Mismatch>>()
    }

fun Type.accepts(v: Value) = check(v).isEmpty()
