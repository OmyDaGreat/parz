@file:Suppress("unused")

package xyz.malefic.parz.idl

import kotlin.math.pow

sealed interface Type {
    data object Empty : Type

    data object Bool : Type

    data class Option<T : Type>(
        val inner: T,
    ) : Type

    data class Lst<T : Type>(
        val inner: T,
    ) : Type

    sealed interface Bounded : Type {
        val overflow: Overflow
        val min: Int // TODO: Switch min and max to BigInt representations to guarantee no overflow
        val max: Int
        val inclusivity: Pair<Inclusivity, Inclusivity>
    }

    data class Str(
        val len: Int = 2.0.pow(32).toInt(),
        override val overflow: Overflow = Overflow.ERROR,
    ) : Bounded {
        override val min: Int = 0
        override val max: Int = len
        override val inclusivity: Pair<Inclusivity, Inclusivity> = Inclusivity.INCLUSIVE to Inclusivity.EXCLUSIVE
    }

    data class Decimal(
        val min: BigInt,
        val max: BigInt,
        val scale: Int,
        val inclusivity: Pair<Inclusivity, Inclusivity>,
        val rounding: Rounding = Rounding.TRUNCATE,
        val overflow: Overflow = Overflow.ERROR,
    )

    sealed interface BigNumeric {
        val base: Base
    }

    data class BigInt(
        val pos: Bool,
        override val base: Base,
    ) : BigNumeric

    data class BigDecimal(
        val unscaled: BigInt,
        val scale: Int,
    ) : BigNumeric {
        override val base: Base = unscaled.base
    }
}

enum class Rounding {
    CEILING,
    FLOOR,
    TRUNCATE,
    AWAY_FROM_ZERO,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
}

enum class Overflow {
    ERROR,
    TRUNCATE_HIGH,
    WRAP,
    SATURATE,
}

enum class Inclusivity {
    INCLUSIVE,
    EXCLUSIVE,
}

data class Base(
    val base: Int,
    val power: Int,
)
