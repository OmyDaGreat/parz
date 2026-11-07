package xyz.malefic.parz.tooling.cli

import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.syntax.SyntaxError
import xyz.malefic.parz.idl.syntax.lex
import xyz.malefic.parz.idl.syntax.parseRecovering
import xyz.malefic.parz.idl.validate

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

private var session = emptyList<Decl>()

private fun run(source: String) =
    try {
        val (schema, syntaxErrors) = parseRecovering(lex(source))
        syntaxErrors.forEach { println(it.describe(source)) }

        var pending = schema.decls.map { it.toDecl() }
        while (true) {
            val problems = validate(session + pending)
            val bad = problems.map { it.decl }.toSet()
            val rejected = pending.filter { it.name in bad }
            if (rejected.isEmpty()) break
            problems.filter { p -> rejected.any { it.name == p.decl } }.forEach { println("${it.decl}: ${it.message}") }
            pending = pending - rejected.toSet()
        }
        session = session + pending
        println(schema.pretty())
        println(schema.toSchema().validate())
    } catch (e: SyntaxError) {
        println(e.describe(source))
    } catch (e: IllegalArgumentException) {
        println("error:  ${e.message}")
    }
