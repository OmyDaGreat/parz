package xyz.malefic.parz.idl.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Overflow
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Rounding
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Rounding.PROHIBITED

/*
 * Decimal arithmetic: rescaling and rounding values, and coercing them into a decimal type's range.
 */

fun Value.Decimal.text(scale: Int = this.scale): String {
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
fun Value.Decimal.rescale(
    newScale: Int,
    rounding: Rounding,
): Value.Decimal {
    if (newScale >= scale) return Value.Decimal(unscaled * BigInteger.TEN.pow(newScale - scale), newScale)

    val divisor = BigInteger.TEN.pow(scale - newScale)
    val (quotient, remainder) = unscaled divrem divisor
    if (remainder.isZero()) return Value.Decimal(quotient, newScale)

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

    return Value.Decimal(if (away) quotient + signedUnit else quotient, newScale)
}

infix fun Value.Decimal.isExactAt(newScale: Int) = newScale >= scale || unscaled % BigInteger.TEN.pow(scale - newScale) == BigInteger.ZERO

/**
 * Coerce a decimal [value] into this type: rescale it to [scale] with [rounding], then apply [overflow] if the result falls outside [min]..[max].
 *
 * [Overflow.TRUNCATE_HIGH] keeps as many low-order digits as the wider of [min] and [max] has, keeping the sign.
 *
 * [Overflow.WRAP] wraps modulo the size of the range, so -128..127 at scale 0 behaves like a signed byte.
 *
 * @throws ArithmeticException if rounding is prohibited and the value is inexact, if [overflow] is [Overflow.ERROR] and the value is out of range, or if truncation still leaves it out of range
 */
infix fun Type.Primitive.Decimal.coerce(value: Value.Decimal): Value.Decimal {
    val rounded = value.rescale(scale, rounding)
    val unscaled = rounded.unscaled
    if (unscaled in min..max) return rounded

    val coerced =
        when (overflow) {
            Overflow.ERROR -> {
                throw ArithmeticException("Decimal overflow: $rounded is outside $min..$max at scale $scale")
            }

            Overflow.TRUNCATE_HIGH -> {
                val digits = maxOf(min.numberOfDecimalDigits(), max.numberOfDecimalDigits())
                (unscaled % BigInteger.TEN.pow(digits)).also {
                    if (it !in min..max) {
                        throw ArithmeticException(
                            "Decimal overflow: $rounded is outside $min..$max at scale $scale after truncation",
                        )
                    }
                }
            }

            Overflow.WRAP -> {
                min + ((unscaled - min) mod (max - min + 1))
            }

            Overflow.SATURATE -> {
                if (unscaled < min) min else max
            }
        }

    return Value.Decimal(coerced, scale)
}
