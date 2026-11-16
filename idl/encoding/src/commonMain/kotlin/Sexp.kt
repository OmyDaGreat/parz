package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.SourceSpan

sealed interface Sexp {
    val span: SourceSpan?

    data class Atom(
        val text: String,
        override val span: SourceSpan? = null,
    ) : Sexp

    data class Str(
        val value: String,
        override val span: SourceSpan? = null,
    ) : Sexp

    data class List(
        val items: kotlin.collections.List<Sexp>,
        override val span: SourceSpan? = null,
    ) : Sexp
}

fun Sexp.print(): String =
    when (this) {
        is Sexp.Atom -> text
        is Sexp.Str -> "\"${value.map { if (it.isISOControl()) "\\u{${it.code}}" else it }}\""
        is Sexp.List -> "(${items.joinToString(" ")})"
    }
