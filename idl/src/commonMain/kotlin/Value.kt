package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Concrete values for the [Type] system, delegating to Kotlin. Used for conformance vectors and literals.
 */
sealed interface Value {
    data object None : Value

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

    data class Seq<T : Value>(
        val items: List<T>,
    ) : Value

    data class Record(
        val fields: Map<String, Value>,
    ) : Value
}
