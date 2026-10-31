package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * The type system for the IDL.
 */
sealed interface Type {
    val kind: String

    /**
     * A void type. Analogous to [Unit].
     */
    data object Void : Type {
        override val kind = "void"
    }

    /**
     * A boolean type.
     */
    data object Bool : Type {
        override val kind = "bool"
    }

    /**
     * An option type (nullability). Allows for nested nullability.
     */
    data class Option(
        val inner: Type,
    ) : Type {
        override val kind = "option"
    }

    /**
     * A sequence type. Analogous to [List].
     */
    data class Seq(
        val inner: Type,
    ) : Type {
        override val kind = "seq"
    }

    /**
     * A string type. Max length is defined, as the JVM defines it, through UTF-16 length.
     */
    data class Str(
        val maxLen: Long = Long.MAX_VALUE,
    ) : Type {
        override val kind = "string"

        init {
            require(maxLen > 0) { "maxLen must be positive" }
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
    ) : Type {
        override val kind = "decimal"

        init {
            require(min <= max) { "min must be less than or equal to max" }
        }

        /**
         * [Decimal]'s rounding modes, inspired by [com.ionspin.kotlin.bignum.decimal.RoundingMode].
         */
        @Suppress("unused")
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

        /**
         * Coerce a decimal [value] into this type: rescale it to [scale] with [rounding], then apply [overflow] if the result falls outside [min]..[max].
         *
         * [Overflow.TRUNCATE_HIGH] keeps as many low-order digits as the wider of [min] and [max] has, keeping the sign.
         *
         * [Overflow.WRAP] wraps modulo the size of the range, so -128..127 at scale 0 behaves like a signed byte.
         *
         * @throws ArithmeticException if rounding is prohibited and the value is inexact, if [overflow] is [Overflow.ERROR] and the value is out of range, or if truncation still leaves it out of range
         */
        infix fun coerce(value: Value.Decimal): Value.Decimal {
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
    }

    /**
     * A record type. Similar to data classes or OCaml's records.
     */
    data class Record(
        val fields: List<Field>,
    ) : Type {
        override val kind = "record"

        init {
            require(fields.isNotEmpty()) { "Record must have at least one field" }
            require(fields.distinctBy { it.name }.size == fields.size) { "Record fields must have unique names" }
        }

        constructor(vararg fields: Field) : this(fields.toList())

        val List<Field>.names
            get() = this.map { it.name }

        /**
         * A record field type. Represents a single field in a record type.
         */
        data class Field(
            val name: String,
            val type: Type,
        ) {
            init {
                require(name.isNotEmpty()) { "Record field name must not be empty" }
            }
        }
    }

    /**
     * An enum type. Similar to [kotlin.Enum].
     */
    @OptIn(ExperimentalStdlibApi::class)
    data class Enum(
        val members: List<Member>,
    ) : Type {
        override val kind = "enum"

        init {
            require(members.isNotEmpty()) { "Enum must have at least one member" }
            require(members.allDistinctBy { it.name }) { "Enum members must have unique names" }
            require(members.allDistinctBy { it.tag }) { "Enum members must have unique tags" }
        }

        constructor(vararg members: Member) : this(members.toList())

        /**
         * An enum member type. Represents a single member in an enum type.
         */
        data class Member(
            val name: String,
            val tag: Int,
        ) {
            init {
                require(name.isNotEmpty()) { "Enum member name must not be empty" }
                require(tag >= 0) { "Enum member tag must be non-negative" }
            }
        }
    }
}
