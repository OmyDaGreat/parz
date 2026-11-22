package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.SourceSpan

/** Whether this character ends an atom: whitespace, a parenthesis, a string quote, or a comment. */
fun Char.isDelimiter() = isWhitespace() || this in "()\";"

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

fun Sexp.pretty(targetWidth: Int = 80) = buildString { layout(this@pretty, indent = 0, trailing = 0, targetWidth) }

private fun StringBuilder.layout(
    sexp: Sexp,
    indent: Int,
    trailing: Int,
    targetWidth: Int,
) {
    val budget = targetWidth - indent - trailing
    if (sexp !is Sexp.List || sexp.items.isEmpty() || sexp.flatWidth(budget) <= budget) {
        write(sexp)
        return
    }
    append('(')
    val last = sexp.items.lastIndex
    sexp.items.forEachIndexed { n, item ->
        val itemIndent = if (n == 0) indent + 1 else indent + 2
        if (n > 0) {
            append('\n')
            repeat(itemIndent) { append(' ') }
        }
        layout(item, itemIndent, if (n == last) trailing + 1 else 0, targetWidth)
    }
    append(')')
}

private fun Sexp.flatWidth(limit: Int): Int =
    when (this) {
        is Sexp.Atom -> {
            text.length
        }

        is Sexp.Str -> {
            buildString { value.forEach { appendEscaped(it) } }.length + 2
        }

        is Sexp.List -> {
            var total = 1
            for ((n, item) in items.withIndex()) {
                if (n > 0) total++
                total += item.flatWidth(limit - total)
                if (total > limit) return total
            }
            total + 1
        }
    }
