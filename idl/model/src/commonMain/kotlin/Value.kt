package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type.Primitive.Decimal.Rounding
import xyz.malefic.parz.idl.Type.Primitive.Decimal.Rounding.PROHIBITED

/**
 * Concrete values for the [Type] system. Used for conformance vectors and literals.
 */
sealed interface Value {
    val kind: String

    data object Void : Value {
        override val kind = "void"
    }

    data class Bool(
        val v: Boolean,
    ) : Value {
        override val kind = "bool"
    }

    class Bytes(
        bytes: ByteArray,
    ) : Value {
        override val kind = "bytes"
        private val data = bytes.copyOf()

        fun toByteArray() = data.copyOf()

        val size get() = data.size

        fun hex() = data.toHexString()

        override fun toString() = hex()

        override fun equals(other: Any?) = other is Bytes && data.contentEquals(other.data)

        override fun hashCode() = data.contentHashCode()
    }

    data class Str(
        val v: String,
    ) : Value {
        override val kind = "string"
    }

    data class Decimal(
        val unscaled: BigInteger,
        val scale: Int,
    ) : Value {
        override val kind = "decimal"

        fun text(scale: Int = this.scale): String {
            var unscaled = unscaled
            if (scale != this.scale) unscaled = rescale(scale, PROHIBITED).unscaled
            if (scale <= 0) return (unscaled * BigInteger.TEN.pow(-scale)).toString()
            val digits = unscaled.abs().toString().padStart(scale + 1, '0')
            val sign = if (unscaled.signum() < 0) "-" else ""
            return sign + digits.dropLast(scale) + "." + digits.takeLast(scale)
        }

        /**
         * Rescale a decimal to a new scale, using [rounding] when the new scale cannot represent the value exactly.
         *
         * @throws ArithmeticException if [rounding] is [Type.Primitive.Decimal.Rounding.PROHIBITED] and the value is inexact at [newScale]
         */
        fun rescale(
            newScale: Int,
            rounding: Rounding,
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
                    Rounding.FLOOR -> signedUnit < 0

                    Rounding.CEILING -> signedUnit > 0

                    Rounding.AWAY_FROM_ZERO -> true

                    Rounding.TOWARDS_ZERO -> false

                    Rounding.ROUND_HALF_AWAY_FROM_ZERO -> half >= 0

                    Rounding.ROUND_HALF_TOWARDS_ZERO -> half > 0

                    Rounding.ROUND_HALF_CEILING -> half > 0 || (half == 0 && signedUnit > 0)

                    Rounding.ROUND_HALF_FLOOR -> half > 0 || (half == 0 && signedUnit < 0)

                    Rounding.ROUND_HALF_TO_EVEN -> half > 0 || (half == 0 && !even)

                    Rounding.ROUND_HALF_TO_ODD -> half > 0 || (half == 0 && even)

                    PROHIBITED -> throw ArithmeticException(
                        "Rounding prohibited: $this is inexact at scale $newScale",
                    )
                }

            return Decimal(if (away) quotient + signedUnit else quotient, newScale)
        }

        infix fun isExactAt(newScale: Int) = newScale >= scale || unscaled % BigInteger.TEN.pow(scale - newScale) == BigInteger.ZERO
    }

    data object None : Value {
        override val kind = "none"
    }

    data class Some(
        val v: Value,
    ) : Value {
        override val kind = "some"
    }

    data class Seq(
        val items: List<Value>,
    ) : Value {
        override val kind = "seq"
    }

    data class Map(
        val entries: kotlin.collections.Map<Value, Value>,
    ) : Value {
        override val kind = "map"
    }

    typealias Fields = kotlin.collections.Map<String, Value>

    data class Record(
        val fields: Fields,
    ) : Value {
        override val kind = "record"
    }

    data class Variant(
        val name: String,
        val payload: Fields = emptyMap(),
    ) : Value {
        override val kind = "variant"
    }
}
