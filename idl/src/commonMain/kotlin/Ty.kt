@file:Suppress("unused")

package xyz.malefic.parz.idl

sealed interface Ty {
    data object Unit : Ty

    data object Str : Ty

    data object Bool : Ty

    sealed interface Bounded : Ty {
        val overflow: Overflow

        val lower: Bound
        val upper: Bound
        val boundType: Pair<BoundType, BoundType>

        data class Bound(
            val pos: Boolean,
            val scale: Int,
            val power: Int,
        )
    }

    data class BoundedInt(
        override val lower: Bounded.Bound,
        override val upper: Bounded.Bound,
        override val boundType: Pair<BoundType, BoundType> = BoundType.EXCLUSIVE to BoundType.EXCLUSIVE,
        override val overflow: Overflow = Overflow.ERROR,
    ) : Ty,
        Bounded

    data class BoundedDecimal(
        val precision: Int,
        val scale: Int,
        val rounding: Rounding = Rounding.TRUNCATE,
        override val overflow: Overflow = Overflow.ERROR,
        override val boundType: Pair<BoundType, BoundType> = BoundType.EXCLUSIVE to BoundType.EXCLUSIVE,
        override val lower: Bounded.Bound = Bounded.Bound(false, 10, precision - scale),
        override val upper: Bounded.Bound = Bounded.Bound(true, 10, precision - scale),
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

enum class BoundType {
    INCLUSIVE,
    EXCLUSIVE,
}
