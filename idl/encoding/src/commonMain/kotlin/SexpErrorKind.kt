package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.DiagnosticKind

sealed interface SexpErrorKind : DiagnosticKind {
    data class UnexpectedCharacter(
        val char: Char,
    ) : SexpErrorKind {
        override val code = "IDL201"
        override val message = "Unexpected character '$char'"
    }

    data object UnexpectedClose : SexpErrorKind {
        override val code = "IDL202"
        override val message = "Unexpected close"
    }

    data object UnclosedList : SexpErrorKind {
        override val code = "IDL203"
        override val message = "Unclosed list"
    }

    data object UnterminatedString : SexpErrorKind {
        override val code = "IDL204"
        override val message = "Unterminated string"
    }

    data object BadEscape : SexpErrorKind {
        override val code = "IDL205"
        override val message = "Bad escape"
    }
}
