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

    data class Decimal(
        val unscaled: BigInteger,
        val scale: Int,
    ) : Value {
        override val kind = "decimal"

        /**
         * Rescale a decimal to a new scale, using [rounding] when the new scale cannot represent the value exactly.
         *
         * @throws ArithmeticException if [rounding] is [Type.Decimal.Rounding.PROHIBITED] and the value is inexact at [newScale]
         */
        fun rescale(
            newScale: Int,
            rounding: Type.Decimal.Rounding,
        ): Decimal {
            if (newScale >= scale) return Decimal(unscaled * BigInteger.TEN.pow(newScale - scale), newScale)

            val divisor = BigInteger.TEN.pow(scale - newScale)
            val (quotient, remainder) = unscaled divrem divisor
            if (remainder.isZero()) return Decimal(quotient, newScale)

            val signedUnit = remainder.signum()
            val half = (remainder.abs() * 2).compareTo(divisor)
            val even = (quotient % 2).isZero()
            val away =
                when (rounding) {
                    Type.Decimal.Rounding.FLOOR -> signedUnit < 0

                    Type.Decimal.Rounding.CEILING -> signedUnit > 0

                    Type.Decimal.Rounding.AWAY_FROM_ZERO -> true

                    Type.Decimal.Rounding.TOWARDS_ZERO -> false

                    Type.Decimal.Rounding.ROUND_HALF_AWAY_FROM_ZERO -> half >= 0

                    Type.Decimal.Rounding.ROUND_HALF_TOWARDS_ZERO -> half > 0

                    Type.Decimal.Rounding.ROUND_HALF_CEILING -> half > 0 || (half == 0 && signedUnit > 0)

                    Type.Decimal.Rounding.ROUND_HALF_FLOOR -> half > 0 || (half == 0 && signedUnit < 0)

                    Type.Decimal.Rounding.ROUND_HALF_TO_EVEN -> half > 0 || (half == 0 && !even)

                    Type.Decimal.Rounding.ROUND_HALF_TO_ODD -> half > 0 || (half == 0 && even)

                    Type.Decimal.Rounding.PROHIBITED -> throw ArithmeticException(
                        "Rounding prohibited: $this is inexact at scale $newScale",
                    )
                }

            return Decimal(if (away) quotient + signedUnit else quotient, newScale)
        }

        infix fun isExactAt(newScale: Int) = newScale >= scale || unscaled % BigInteger.TEN.pow(scale - newScale) == BigInteger.ZERO
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
