package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.idl.Reason

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
}
