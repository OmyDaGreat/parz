package xyz.malefic.parz.core

fun interface Sink<in E> {
    fun report(e: E)
}

data class Checked<out R, out E>(
    val value: R,
    val diagnostics: List<E>,
) {
    val ok get() = diagnostics.isEmpty()
}

fun <R, E> collecting(block: context(Sink<E>) () -> R): Checked<R, E> {
    val diagnostics = mutableListOf<E>()
    val result = block { diagnostics += it }
    return Checked(result, diagnostics)
}

enum class Severity {
    ERROR,
    WARNING,
    INFO,
}

interface DiagnosticKind {
    val code: String
    val message: String
    val severity: Severity get() = Severity.ERROR
}

data class SourceSpan(
    val start: Int,
    val end: Int,
) {
    init {
        require(start <= end) { "Invalid source span: start ($start) must be <= end ($end)" }
        require(start >= 0) { "Invalid source span: start ($start) must be >= 0" }
        require(end >= 0) { "Invalid source span: end ($end) must be >= 0" }
    }
}

data class Diagnostic<out K : DiagnosticKind>(
    val kind: K,
    val span: SourceSpan?,
)
