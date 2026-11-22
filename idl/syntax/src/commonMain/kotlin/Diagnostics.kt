package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.idl.model.ShapeErrorKind

typealias SyntaxDiagnostic = Diagnostic<SyntaxErrorKind>

sealed interface SyntaxErrorKind : DiagnosticKind {
    data class UnexpectedCharacter(
        val char: Char,
    ) : SyntaxErrorKind {
        override val code = "IDL001"
        override val message = "Unexpected character '$char'"
    }

    data class InlineDecl(
        val keyword: String,
    ) : SyntaxErrorKind {
        override val code = "IDL002"
        override val message = "Inline $keyword declarations are not allowed"
    }

    data class ExpectedDeclKeyword(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL003"
        override val message = "Expected 'record', 'variant', 'enum', or 'type', but instead found ${found.pretty()}"
    }

    data class ExpectedSymbol(
        val expected: Char,
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL004"
        override val message = "Expected '$expected', but instead found ${found.pretty()}"
    }

    data class ExpectedWord(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL005"
        override val message = "Expected a word, but instead found ${found.pretty()}"
    }

    data class ExpectedInt(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL006"
        override val message = "Expected an integer, but instead found ${found.pretty()}"
    }

    data class ExpectedOption(
        val options: List<String>,
        val found: String,
    ) : SyntaxErrorKind {
        override val code = "IDL007"
        override val message = "Expected one of ${options.joinToString { "'$it'" }}, but instead found '$found'"
    }

    data class PrecisionOutOfRange(
        val value: BigInteger,
    ) : SyntaxErrorKind {
        override val code = "IDL008"
        override val message = "Precision must be between 0 and 38"
    }

    data class ExpectedDecimalBounds(
        val found: Token,
    ) : SyntaxErrorKind {
        override val code = "IDL009"
        override val message = "Expected ',' or '..' in the decimal type, but instead found ${found.pretty()}"
    }

    data class InvalidType(
        val error: ShapeErrorKind,
    ) : SyntaxErrorKind {
        override val code = error.code
        override val message = error.message
    }

    data class IntegerOutOfRange(
        val value: BigInteger,
        val target: String,
    ) : SyntaxErrorKind {
        override val code = "IDL011"
        override val message = "Integer value $value is out of range for $target"
    }

    data class EmptyPayload(
        val memberName: String,
    ) : SyntaxErrorKind {
        override val code = "IDL012"
        override val message = "Payload of variant member '$memberName' must not be empty if parentheses are used"
    }
}
