package xyz.malefic.parz.idl.model

import com.ionspin.kotlin.bignum.integer.BigInteger

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
