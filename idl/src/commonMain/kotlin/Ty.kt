@file:Suppress("unused")

package xyz.malefic.parz.idl

import kotlin.math.pow

sealed interface Ty {
    data object Empty : Ty

    data class Str(
        val len: Int = 2.0.pow(32).toInt(),
    ) : Bounded {
        override val overflow: Overflow = Overflow.ERROR
        override val min: Int = 0
        override val max: Int = len
        override val inclusivity: Pair<Inclusivity, Inclusivity> = Inclusivity.INCLUSIVE to Inclusivity.EXCLUSIVE
    }

    data object Bool : Ty

    sealed interface Numeric : Bounded {
        val scale: Int
    }

    sealed interface Bounded : Ty {
        val overflow: Overflow
        val min: Int // TODO: Switch min and max to BigInt representations to guarantee no overflow
        val max: Int
        val inclusivity: Pair<Inclusivity, Inclusivity>
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
