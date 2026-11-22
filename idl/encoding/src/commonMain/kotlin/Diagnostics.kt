package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.idl.model.Reason
import xyz.malefic.parz.idl.model.Reason.Fit.InexactLength
import xyz.malefic.parz.idl.model.Reason.Fit.InexactScale
import xyz.malefic.parz.idl.model.Reason.Fit.OutOfRange
import xyz.malefic.parz.idl.model.Reason.Fit.TooLong
import xyz.malefic.parz.idl.model.Reason.Fit.TooShort
import xyz.malefic.parz.idl.model.Value

sealed interface SexpErrorKind : DiagnosticKind {
    data object UnexpectedClose : SexpErrorKind {
        override val code = "IDL201"
        override val message = "Unexpected ')'"
    }

    data object UnclosedList : SexpErrorKind {
        override val code = "IDL202"
        override val message = "Unclosed '('"
    }

    data object UnterminatedString : SexpErrorKind {
        override val code = "IDL203"
        override val message = "Unterminated string"
    }

    data object BadEscape : SexpErrorKind {
        override val code = "IDL204"
        override val message = "Invalid escape sequence"
    }
}

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
                is OutOfRange -> "Out of range (unscaled ${reason.min}..${reason.max})"
                is InexactScale -> "Too many decimal places (at most ${reason.expected})"
                is TooLong -> "Too long (${reason.actual} > ${reason.maxLen})"
                is TooShort -> "Too short (${reason.actual} < ${reason.minLen})"
                is InexactLength -> "Expected a length of ${reason.exactLen}, got ${reason.actual}"
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
