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

typealias Errors<E> = Checked<Unit, E>

fun <R, E> collecting(block: context(Sink<E>) () -> R): Checked<R, E> {
    val diagnostics = mutableListOf<E>()
    val result = block { diagnostics += it }
    return Checked(result, diagnostics)
}

context(sink: Sink<AnyDiagnostic>)
fun sink() = sink
