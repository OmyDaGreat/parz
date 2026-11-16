package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import kotlin.test.assertEquals

fun read(source: String) = collecting<List<Sexp>, Diagnostic<SexpErrorKind>> { readSexps(source) }

/**
 * Read [source], failing the test if it has any errors.
 */
fun readOk(source: String): List<Sexp> {
    val (value, diagnostics) = read(source)
    assertEquals(emptyList(), diagnostics, "unexpected errors reading:\n$source")
    return value
}

/**
 * Read [source], which must hold exactly one S-expression and no errors.
 */
fun one(source: String): Sexp {
    val all = readOk(source)
    assertEquals(1, all.size, "expected one S-expression, got $all")
    return all.single()
}

fun atom(text: String) = Sexp.Atom(text)

fun str(value: String) = Sexp.Str(value)

fun list(vararg items: Sexp) = Sexp.List(items.toList())

/**
 * This tree with every span removed, for comparing structure only.
 */
fun Sexp.withoutSpans(): Sexp =
    when (this) {
        is Sexp.Atom -> copy(span = null)
        is Sexp.Str -> copy(span = null)
        is Sexp.List -> Sexp.List(items.map { it.withoutSpans() })
    }

fun List<Sexp>.withoutSpans() = map { it.withoutSpans() }

/**
 * The span of the [nth] occurrence (counting from 0) of [text] in this source.
 */
fun String.span(
    text: String,
    nth: Int = 0,
): SourceSpan {
    var index = -1
    repeat(nth + 1) {
        index = indexOf(text, index + 1)
        check(index >= 0) { "'$text' (occurrence $nth) is not in the source" }
    }
    return SourceSpan(index, index + text.length)
}
