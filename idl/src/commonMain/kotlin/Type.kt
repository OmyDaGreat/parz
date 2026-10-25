@file:Suppress("unused")

package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * A basic type system for the IDL, implemented in Kotlin for testing before setting up the external schema.
 */
sealed interface Type {
    /**
     * An empty type. Analogous to [Unit].
     */
    data object Empty : Type

    /**
     * A boolean type.
     */
    data object Bool : Type

    /**
     * An option type (nullability). Allows for nested nullability.
     */
    data class Option(
        val inner: Type,
    ) : Type

    /**
     * A sequence type. Analogous to [List].
     */
    data class Seq(
        val inner: Type,
    ) : Type

    /**
     * A string type. Max length is defined, as the JVM defines it, through UTF-16 length.
     */
    data class Str(
        val maxLen: Long = Long.MAX_VALUE,
    ) : Type {
        init {
            require(maxLen > 0) { "maxLen must be positive" }
        }
    }

    /**
     * A decimal type. [min] and [max] are unscaled big integers, allowing for easier comparison with the unscaled value of a decimal.
     */
    data class Decimal(
        val min: BigInteger,
        val max: BigInteger,
        val scale: Int,
        val rounding: Rounding = Rounding.TRUNCATE,
        val overflow: Overflow = Overflow.ERROR,
    ) : Type {
        init {
            require(min <= max) { "min must be less than or equal to max" }
        }

        companion object {
            /**
             * Rounding modes for [Type.Decimal].
             *
             * [Rounding.CEILING] - Round towards positive infinity.
             *
             * [Rounding.FLOOR] - Round towards negative infinity.
             *
             * [Rounding.TRUNCATE] - Round towards zero.
             *
             * [Rounding.AWAY_FROM_ZERO] - Round away from zero.
             *
             * [Rounding.HALF_UP] - Round half towards positive infinity.
             *
             * [Rounding.HALF_DOWN] - Round half towards negative infinity.
             *
             * [Rounding.HALF_EVEN] - Round half to nearest even integer.
             *
             * [Rounding.PROHIBITED] - Prohibit rounding.
             */
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

            /**
             * Overflow modes for [Type.Decimal].
             *
             * [Overflow.ERROR] - Throw an exception.
             *
             * [Overflow.TRUNCATE_HIGH] - Truncate the high bits.
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
     * A record type. Similar to data classes or OCaml's records.
     */
    data class Record(
        val fields: List<Field>,
    ) : Type {
        init {
            require(fields.isNotEmpty()) { "Record must have at least one field" }
            require(fields.distinctBy { it.name }.size == fields.size) { "Record fields must have unique names" }
        }

        val List<Field>.names
            get() = this.map { it.name }

        /**
         * A record field type. Represents a single field in a record type.
         */
        data class Field(
            val name: String,
            val type: Type,
        )
    }

    /**
     * An enum type. Analogous to [kotlin.Enum].
     */
    data class Enum(
        val members: List<Member>,
    ) : Type {
        init {
            require(members.isNotEmpty()) { "Enum must have at least one member" }
            require(members.distinctBy { it.name }.size == members.size) { "Enum members must have unique names" }
            require(members.distinctBy { it.tag }.size == members.size) { "Enum members must have unique tags" }
            require(members.all { it.tag >= BigInteger.ZERO }) { "Enum members must have non-negative tags" }
        }

        /**
         * An enum member type. Represents a single member in an enum type.
         */
        data class Member(
            val name: String,
            val tag: BigInteger,
        )
    }
}
