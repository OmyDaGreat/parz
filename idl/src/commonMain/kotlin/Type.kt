@file:Suppress("unused")

package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger

sealed interface Type {
    data object Empty : Type

    data object Bool : Type

    data class Option(
        val inner: Type,
    ) : Type

    data class Seq(
        val inner: Type,
    ) : Type

    data class Str(
        val maxLen: Long = Long.MAX_VALUE,
    ) : Type {
        init {
            require(maxLen > 0) { "maxLen must be positive" }
        }
    }

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
    }

    data class Record(
        val fields: List<Field>,
    ) : Type {
        init {
            require(fields.isNotEmpty()) { "Record must have at least one field" }
            require(fields.distinctBy { it.name }.size == fields.size) { "Record fields must have unique names" }
        }

        data class Field(
            val name: String,
            val type: Type,
        )
    }

    data class Enum(
        val members: List<Member>,
    ) : Type {
        init {
            require(members.isNotEmpty()) { "Enum must have at least one member" }
            require(members.distinctBy { it.name }.size == members.size) { "Enum members must have unique names" }
            require(members.distinctBy { it.tag }.size == members.size) { "Enum members must have unique tags" }
            require(members.all { it.tag >= BigInteger.ZERO }) { "Enum members must have non-negative tags" }
        }

        data class Member(
            val name: String,
            val tag: BigInteger,
        )
    }
}

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

enum class Overflow {
    ERROR,
    TRUNCATE_HIGH,
    WRAP,
    SATURATE,
}
