package xyz.malefic.parz.idl.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyRange
import xyz.malefic.parz.idl.model.ShapeErrorKind.NegativeLength
import xyz.malefic.parz.idl.model.ShapeErrorKind.NonPositiveLength

/**
 * The type system for the IDL.
 */
sealed interface Type {
    val kind: String

    /**
     * A primitive type. Can be a void, bool, str, or decimal
     */
    sealed interface Primitive : Type {
        /**
         * A void type. Analogous to [Unit].
         */
        data object Void : Primitive {
            override val kind = "void"
        }

        /**
         * A boolean type.
         */
        data object Bool : Primitive {
            override val kind = "bool"
        }

        /**
         * A bytes type.
         * @param minLen The minimum length of the bytes.
         * @param maxLen The maximum length of the bytes.
         */
        data class Bytes(
            val minLen: Long,
            val maxLen: Long,
        ) : Primitive {
            /**
             * A bytes type with an exact, predetermined length.
             * @param exactLen The exact length of the bytes.
             */
            constructor(exactLen: Long) : this(exactLen, exactLen)

            /**
             * A bytes type, with length defined through a range.
             * @param range The range for the length of the bytes.
             */
            constructor(range: ClosedRange<Long>) : this(range.start, range.endInclusive)

            override val kind = "bytes"

            /**
             * Whether this is a bytes type with an exact length.
             */
            val exact = minLen == maxLen

            init {
                requireShape(NegativeLength.check(minLen))
                requireShape(EmptyRange.check(minLen, maxLen))
            }
        }

        /**
         * A string type. Max length is defined, as the JVM defines it, through UTF-16 length.
         */
        data class Str(
            val maxLen: Long = Long.MAX_VALUE,
        ) : Primitive {
            override val kind = "string"

            init {
                requireShape(NonPositiveLength.check(maxLen))
            }
        }

        /**
         * A decimal type. [min] and [max] are inclusive unscaled big integers, allowing for easier comparison with the unscaled value of a decimal.
         *
         * [Decimal] types with [scale] of 0 are referred to as Integers.
         */
        data class Decimal(
            val min: BigInteger,
            val max: BigInteger,
            val scale: Int,
            val rounding: Rounding = Rounding.TOWARDS_ZERO,
            val overflow: Overflow = Overflow.ERROR,
        ) : Primitive {
            override val kind = "decimal"

            init {
                requireShape(EmptyRange.check(min, max))
            }

            /**
             * [Decimal]'s rounding modes, inspired by [com.ionspin.kotlin.bignum.decimal.RoundingMode].
             */
            enum class Rounding {
                /**
                 * Rounds towards negative infinity.
                 */
                FLOOR,

                /**
                 * Rounds towards positive infinity.
                 */
                CEILING,

                /**
                 * Rounds away from zero.
                 */
                AWAY_FROM_ZERO,

                /**
                 * Rounds towards zero (truncation).
                 */
                TOWARDS_ZERO,

                /**
                 * Rounds towards nearest value at the target's scale, using away from zero as tiebreaker.
                 */
                ROUND_HALF_AWAY_FROM_ZERO,

                /**
                 * Rounds towards nearest value at the target's scale, using towards zero as a tiebreaker.
                 */
                ROUND_HALF_TOWARDS_ZERO,

                /**
                 * Rounds towards nearest value at the target's scale, using towards infinity as tiebreaker.
                 */
                ROUND_HALF_CEILING,

                /**
                 * Rounds towards the nearest value at the target's scale, using towards negative infinity as a tiebreaker.
                 */
                ROUND_HALF_FLOOR,

                /**
                 * Rounds towards the nearest even value at the target's scale.
                 */
                ROUND_HALF_TO_EVEN,

                /**
                 * Rounds towards the nearest odd value at the target's scale.
                 */
                ROUND_HALF_TO_ODD,

                /**
                 * Prohibits rounding.
                 */
                PROHIBITED,
            }

            /**
             * [Decimal]'s overflow modes.
             *
             * [Overflow.ERROR] - Throw an error.
             *
             * [Overflow.TRUNCATE_HIGH] - Truncate the higher-order digits.
             *
             * [Overflow.WRAP] - Wrap around.
             *
             * [Overflow.SATURATE] - Stop at the max/min value.
             */
            enum class Overflow {
                ERROR,
                TRUNCATE_HIGH,
                WRAP,
                SATURATE,
            }
        }
    }

    /**
     * A collection type. Can be an option, a sequence, or a map.
     */
    sealed interface Collection : Type {
        /**
         * An option type (nullability). Allows for nested nullability.
         */
        data class Option(
            val inner: Type,
        ) : Collection {
            override val kind = "option"
        }

        /**
         * A sequence type. Analogous to [List].
         */
        data class Seq(
            val inner: Type,
        ) : Collection {
            override val kind = "seq"
        }

        /**
         * A map type. Analogous to [kotlin.collections.Map].
         */
        data class Map(
            val keys: Type,
            val values: Type,
        ) : Collection {
            override val kind = "map"
        }
    }

    data class Ref(
        val name: String,
    ) : Type {
        override val kind = "ref"
    }
}
