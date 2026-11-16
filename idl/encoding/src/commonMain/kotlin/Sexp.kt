package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.SourceSpan

sealed interface Sexp {
    val span: SourceSpan?

    data class Atom(
        val text: String,
        override val span: SourceSpan? = null,
    ) : Sexp {
        init {
            require(text.isNotEmpty() && text.none { it.isDelimiter() }) { "Invalid atom: '$text'" }
        }
    }

    data class Str(
        val value: String,
        override val span: SourceSpan? = null,
    ) : Sexp

    data class List(
        val items: kotlin.collections.List<Sexp>,
        override val span: SourceSpan? = null,
    ) : Sexp
}

fun Sexp.print(): String = buildString { write(this@print) }

private fun StringBuilder.write(sexp: Sexp) {
    when (sexp) {
        is Sexp.Atom -> {
            append(sexp.text)
        }

        is Sexp.Str -> {
            append('"')
            sexp.value.forEach { appendEscaped(it) }
            append('"')
        }

        is Sexp.List -> {
            append('(')
            sexp.items.forEachIndexed { n, item ->
                if (n > 0) append(' ')
                write(item)
            }
            append(')')
        }
    }
}

private fun StringBuilder.appendEscaped(c: Char) {
    when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c.isISOControl()) append("\\u{").append(c.code.toString(16)).append('}') else append(c)
    }
}
