@file:Suppress("unused")

package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger

sealed interface Type {
    data object Empty : Type

    data object Bool : Type

    data class Option(
        val inner: Type,
    ) : Type

    data class Seq(
        val items: List<Value>,
    ) : Type

    data class Str(
        val maxLen: BigInteger = BigInteger.TWO.pow(32),
    )

    data class Decimal(
        val min: BigInteger,
        val max: BigInteger,
        val scale: Int,
        val rounding: Rounding = Rounding.TRUNCATE,
        val overflow: Overflow = Overflow.ERROR,
    ) : Type
}

enum class Rounding {
    CEILING,
    FLOOR,
    TRUNCATE,
    AWAY_FROM_ZERO,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    PROHIBITED,
}

enum class Overflow {
    ERROR,
    TRUNCATE_HIGH,
    WRAP,
    SATURATE,
}

data class Base(
    val base: Int,
    val power: Int,
)
