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
) {
    fun render(source: String): String {
        require(span != null) { "Cannot render diagnostic without a source span" }
        val before = source.substring(0..<span.start.coerceAtMost(source.length))
        val line = before.count { it == '\n' } + 1
        val lineStart = before.lastIndexOf('\n') + 1
        val column = span.start - lineStart + 1
        val text = source.substring(lineStart).substringBefore('\n')
        return "${kind.severity}: ${kind.message} at line $line:$column\n$text\n${" ".repeat(column - 1)}^"
    }
}
