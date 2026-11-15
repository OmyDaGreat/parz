package xyz.malefic.parz.core

/**
 * A diagnostic kind for tests, with a fixed code and a configurable message.
 */
data class TestKind(
    override val message: String = "boom",
) : DiagnosticKind {
    override val code = "T001"
}

fun diagnostic(
    start: Int,
    end: Int,
    message: String = "boom",
) = Diagnostic(TestKind(message), SourceSpan(start, end))
