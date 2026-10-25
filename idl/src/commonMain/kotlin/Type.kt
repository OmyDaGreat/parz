@file:Suppress("unused")

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
        override val kind = "option{${inner.kind}}"
    }

    /**
     * A sequence type. Analogous to [List].
     */
    data class Seq(
        val inner: Type,
    ) : Type {
        override val kind = "seq{${inner.kind}}"
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
     * A decimal type. [min] and [max] are unscaled big integers, allowing for easier comparison with the unscaled value of a decimal.
     */
    data class Decimal(
        val min: BigInteger,
        val max: BigInteger,
        val scale: Int,
        val rounding: Rounding = Rounding.TOWARDS_ZERO,
        val overflow: Overflow = Overflow.ERROR,
    ) : Type {
        override val kind = "decimal{min=$min, max=$max, scale=$scale, rounding=$rounding, overflow=$overflow}"

        init {
            require(min <= max) { "min must be less than or equal to max" }
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
             * Rounds towards nearest integer, using away from zero as tiebreaker.
             */
            ROUND_HALF_AWAY_FROM_ZERO,

            /**
             * Rounds towards nearest integer, using towards zero as a tiebreaker.
             */
            ROUND_HALF_TOWARDS_ZERO,

            /**
             * Rounds towards nearest integer, using towards infinity as tiebreaker.
             */
            ROUND_HALF_CEILING,

            /**
             * Rounds towards the nearest integer, using towards negative infinity as a tiebreaker.
             */
            ROUND_HALF_FLOOR,

            /**
             * Rounds towards the nearest even integer.
             */
            ROUND_HALF_TO_EVEN,

            /**
             * Rounds towards the nearest odd integer.
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

    /**
     * A record type. Similar to data classes or OCaml's records.
     */
    data class Record(
        val fields: List<Field>,
    ) : Type {
        override val kind = "record{${fields.joinToString("; ") { "${it.name}, ${it.type.kind}" }}}"

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
     * An enum type. Similar to [kotlin.Enum].
     */
    data class Enum(
        val members: List<Member>,
    ) : Type {
        override val kind = "enum{${members.joinToString("; ") { "${it.name}, ${it.tag}" }}}"

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
