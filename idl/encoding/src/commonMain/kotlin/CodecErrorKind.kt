package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.idl.Reason
import xyz.malefic.parz.idl.Value

sealed interface CodecErrorKind : DiagnosticKind {
    data class Expected(
        val what: String,
    ) : CodecErrorKind {
        override val code = "IDL210"
        override val message = "Expected $what"
    }

    data class UnknownMember(
        val name: String,
    ) : CodecErrorKind {
        override val code = "IDL211"
        override val message = "Unknown member '$name'"
    }

    data class MissingField(
        val name: String,
    ) : CodecErrorKind {
        override val code = "IDL212"
        override val message = "Missing field '$name'"
    }

    data class UnknownField(
        val name: String,
    ) : CodecErrorKind {
        override val code = "IDL213"
        override val message = "Unknown field '$name'"
    }

    data class DuplicateField(
        val name: String,
    ) : CodecErrorKind {
        override val code = "IDL214"
        override val message = "Duplicate field '$name'"
    }

    data class DoesNotFit(
        val reason: Reason,
    ) : CodecErrorKind {
        override val code = "IDL215"
        override val message =
            when (reason) {
                is Reason.OutOfRange -> "Out of range (unscaled ${reason.min}..${reason.max})"
                is Reason.InexactScale -> "Too many decimal places (at most ${reason.expected})"
                is Reason.TooLong -> "Too long (${reason.actual} > ${reason.maxLen})"
                else -> "Value does not fit: $reason"
            }
    }

    data class ExpectedOneValue(
        val found: Int,
    ) : CodecErrorKind {
        override val code = "IDL216"
        override val message = "Expected exactly one value, found $found"
    }

    data object MissingHeader : CodecErrorKind {
        override val code = "IDL217"
        override val message = "Missing header"
    }

    data class UnsupportedVersion(
        val found: String,
    ) : CodecErrorKind {
        override val code = "IDL218"
        override val message = "Unsupported format version '$found'"
    }

    data class SchemaMismatch(
        val expected: SchemaId,
        val found: String,
    ) : CodecErrorKind {
        override val code = "IDL219"
        override val message = "Schema mismatch: expected '$expected', found '$found'"
    }

    data class UnknownRootType(
        val name: String,
    ) : CodecErrorKind {
        override val code = "IDL220"
        override val message = "Unknown root type '$name'"
    }

    data class WrongRootType(
        val expected: String,
        val found: String,
    ) : CodecErrorKind {
        override val code = "IDL221"
        override val message = "Wrong root type: expected '$expected', found '$found'"
    }

    data class DuplicateKey(
        val key: Value,
    ) : CodecErrorKind {
        override val code = "IDL222"
        override val message = "Duplicate key '$key'"
    }
}
