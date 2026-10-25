package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Concrete values for the [Type] system. Used for conformance vectors and literals.
 */
sealed interface Value {
    val kind: String

    data object Void : Value {
        override val kind = "void"
    }

    data object None : Value {
        override val kind = "none"
    }

    data class Some(
        val v: Value,
    ) : Value {
        override val kind = "some"
    }

    data class Bool(
        val v: Boolean,
    ) : Value {
        override val kind = "bool"
    }

    data class Dec(
        val unscaled: BigInteger,
        val scale: Int,
    ) : Value {
        override val kind = "decimal"
    }

    data class Str(
        val v: String,
    ) : Value {
        override val kind = "string"
    }

    data class Seq(
        val items: List<Value>,
    ) : Value {
        override val kind = "seq"
    }

    data class Record(
        val fields: Map<String, Value>,
    ) : Value {
        override val kind = "record"
    }

    data class Enum(
        val name: String,
    ) : Value {
        override val kind = "enum"
    }
}
