@file:Suppress("unused")

package xyz.malefic.parz.idl

sealed interface Ty {
    data object Unit : Ty

    data object Str : Ty

    data object Bool : Ty

    sealed interface Bounded : Ty {
        val overflow: Overflow

        val lower: BoundValue
        val upper: BoundValue
        val inclusivity: Pair<Inclusivity, Inclusivity>
    }

    data class BoundedInt(
        override val lower: BoundValue,
        override val upper: BoundValue,
        override val inclusivity: Pair<Inclusivity, Inclusivity> = Inclusivity.EXCLUSIVE to Inclusivity.EXCLUSIVE,
        override val overflow: Overflow = Overflow.ERROR,
    ) : Ty,
        Bounded

    data class BoundedDecimal(
        val precision: Int,
        val scale: Int,
        val rounding: Rounding = Rounding.TRUNCATE,
        override val overflow: Overflow = Overflow.ERROR,
        override val inclusivity: Pair<Inclusivity, Inclusivity> = Inclusivity.EXCLUSIVE to Inclusivity.EXCLUSIVE,
        override val lower: BoundValue = BoundValue.Bound(false, 10, precision - scale),
        override val upper: BoundValue = BoundValue.Bound(true, 10, precision - scale),
    ) : Ty,
        Bounded {
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
    }
}

enum class Overflow {
    ERROR,
    WRAP,
    SATURATE,
}

enum class Inclusivity {
    INCLUSIVE,
    EXCLUSIVE,
}

sealed interface BoundValue {
    data class Bound(
        val pos: Boolean,
        val base: Int,
        val power: Int,
    ) : BoundValue

    data object Zero : BoundValue
}
