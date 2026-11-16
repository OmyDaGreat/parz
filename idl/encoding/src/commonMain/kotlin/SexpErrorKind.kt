package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.DiagnosticKind

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
