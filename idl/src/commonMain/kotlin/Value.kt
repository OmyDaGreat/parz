package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Concrete values for the [Type] system. Used for conformance vectors and literals.
 */
sealed interface Value {
    data object None : Value

    data class Some(
        val v: Value,
    ) : Value

    data class Bool(
        val v: Boolean,
    ) : Value

    data class Dec(
        val unscaled: BigInteger,
        val scale: Int,
    ) : Value

    data class Str(
        val v: String,
    ) : Value

    data class Seq(
        val items: List<Value>,
    ) : Value

    data class Record(
        val fields: Map<String, Value>,
    ) : Value

    data class Enum(
        val name: String,
    ) : Value
}
