package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Ty.Overflowable.Overflow

@Suppress("unused")
sealed interface Ty {
    data object Unit : Ty

    data object Str : Ty

    data object Bool : Ty

    data class BoundedInt(
        override val lower: Bounded.Bound,
        override val upper: Bounded.Bound,
        override val boundType: Bounded.BoundType = Bounded.BoundType.Inclusive,
        override val overflow: Overflow = Overflow.ERROR,
    ) : Ty,
        Bounded

    data class BoundedDecimal(
        val precision: Int,
        val scale: Int,
        val rounding: Rounding = Rounding.TRUNCATE,
        override val overflow: Overflow = Overflow.ERROR,
        override val boundType: Bounded.BoundType = Bounded.BoundType.Inclusive,
        override val lower: Bounded.Bound = Bounded.Bound(false, 10, precision),
        override val upper: Bounded.Bound = Bounded.Bound(true, 10, precision),
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

    interface Overflowable {
        val overflow: Overflow

        enum class Overflow {
            ERROR,
            WRAP,
            SATURATE,
        }
    }

    interface Bounded : Overflowable {
        sealed class BoundType {
            data object Inclusive : BoundType()

            data object Exclusive : BoundType()
        }

        data class Bound(
            val pos: Boolean,
            val scale: Int,
            val power: Int,
        )

        val lower: Bound
        val upper: Bound
        val boundType: BoundType
    }
}
