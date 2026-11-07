package xyz.malefic.parz.tooling.cli

import xyz.malefic.parz.idl.syntax.SyntaxError
import xyz.malefic.parz.idl.syntax.lex
import xyz.malefic.parz.idl.syntax.parse

fun main() {
    val buffer = StringBuilder()
    while (true) {
        val line = readlnOrNull() ?: break
        val trimmed = line.trimEnd()
        if (trimmed.endsWith(";;")) {
            buffer.appendLine(trimmed.removeSuffix(";;"))
            run(buffer.toString())
            buffer.clear()
        } else {
            buffer.appendLine(line)
        }
    }
    if (buffer.isNotBlank()) run(buffer.toString())
}

private fun run(source: String) =
    try {
        val schemaSyntax = parse(lex(source))
        println(schemaSyntax.pretty())
        println(schemaSyntax.toSchema().validate())
    } catch (e: SyntaxError) {
        println(e.describe(source))
    }
