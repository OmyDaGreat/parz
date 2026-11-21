package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.syntax.DeclSyntax.Companion.locate
import xyz.malefic.parz.idl.validate
import kotlin.test.assertEquals

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

/**
 * Parse [source], failing the test if it has any syntax errors.
 */
fun parsed(source: String): SchemaSyntax {
    val (value, diagnostics) = parseSource(source)
    assertEquals(emptyList(), diagnostics, "unexpected syntax errors in:\n$source")
    return value
}

fun errors(source: String) = parseSource(source).diagnostics

/**
 * The single syntax error in [source].
 */
fun error(source: String): SyntaxDiagnostic {
    val all = errors(source)
    assertEquals(1, all.size, "expected exactly one syntax error, got $all")
    return all.single()
}

inline fun <reified D : DeclSyntax> decl(source: String) = parsed(source).decls.single() as D

/**
 * The syntax for [text] used as a type.
 */
fun typeOf(text: String) = decl<DeclSyntax.Alias>("type T = $text").type

/**
 * Lower each declaration in [schema] to the model, collecting shape errors.
 */
fun lower(schema: SchemaSyntax) = collecting { schema.decls.map { it.toDecl() } }

fun lowered(source: String) = lower(parsed(source))

/**
 * Validate [source] and return each schema error's code with the source text it is located at.
 */
fun located(source: String): List<Pair<String, SourceSpan?>> {
    val pairs =
        collecting {
            parsed(source).decls.mapNotNull { s ->
                s.toDecl()?.let { s to it }
            }
        }
    assertEquals(emptyList(), pairs.diagnostics)
    return validate(pairs.value.map { it.second }).diagnostics.map { it.kind.code to pairs.value.locate(it.kind) }
}
