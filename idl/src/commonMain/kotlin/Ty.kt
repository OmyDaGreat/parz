@file:Suppress("unused")

package xyz.malefic.parz.idl

import kotlin.math.pow

sealed interface Ty {
    data object Empty : Ty

    data object Bool : Ty

    sealed interface Bounded : Ty {
        val overflow: Overflow
        val min: Int // TODO: Switch min and max to BigInt representations to guarantee no overflow
        val max: Int
        val inclusivity: Pair<Inclusivity, Inclusivity>
    }

    data class Str(
        val len: Int = 2.0.pow(32).toInt(),
    ) : Bounded {
        override val overflow: Overflow = Overflow.ERROR
        override val min: Int = 0
        override val max: Int = len
        override val inclusivity: Pair<Inclusivity, Inclusivity> = Inclusivity.INCLUSIVE to Inclusivity.EXCLUSIVE
    }

    sealed interface Numeric {
        val scale: Int
    }

    data object Int32 : Numeric, Bounded {
        override val scale: Int = 0
        override val overflow: Overflow = Overflow.ERROR
        override val min: Int = -2.0.pow(31).toInt()
        override val max: Int = 2.0.pow(31).toInt() - 1
        override val inclusivity: Pair<Inclusivity, Inclusivity> = Inclusivity.INCLUSIVE to Inclusivity.EXCLUSIVE
    }

    data object Int64 : Numeric, Bounded {
        override val scale: Int = 0
        override val overflow: Overflow = Overflow.ERROR
        override val min: Int = -2.0.pow(63).toInt()
        override val max: Int = 2.0.pow(63).toInt() - 1
        override val inclusivity: Pair<Inclusivity, Inclusivity> = Inclusivity.INCLUSIVE to Inclusivity.EXCLUSIVE
    }

    sealed interface BigNumeric : Numeric {
        val base: Base
    }

    data class BigInt(
        val sign: Int,
        val digits: List<Int>,
        override val base: Base,
    ) : BigNumeric {
        override val scale: Int = 0
    }

    data class BigDecimal(
        val unscaled: BigInt,
        override val scale: Int,
    ) : BigNumeric {
        override val base: Base = unscaled.base
    }
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
