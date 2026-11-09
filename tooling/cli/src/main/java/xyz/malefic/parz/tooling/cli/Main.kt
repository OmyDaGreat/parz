package xyz.malefic.parz.tooling.cli

import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.syntax.DeclSyntax.Companion.locate
import xyz.malefic.parz.idl.syntax.SchemaSyntax
import xyz.malefic.parz.idl.syntax.lex
import xyz.malefic.parz.idl.syntax.parse
import xyz.malefic.parz.idl.validate

fun main(vararg args: String) {
    val debug = args.contains("--debug") || args.contains("-d")
    val buffer = StringBuilder()
    while (true) {
        val line = readlnOrNull() ?: break
        val trimmed = line.trimEnd()
        if (trimmed.endsWith(";;")) {
            buffer.appendLine(trimmed.removeSuffix(";;"))
            run(buffer.toString(), debug)
            buffer.clear()
        } else {
            buffer.appendLine(line)
        }
    }
    if (buffer.isNotBlank()) run(buffer.toString())
}

private var session = emptyList<Decl>()

private fun run(
    source: String,
    debug: Boolean = false,
) = try {
    val (schema, syntaxErrors) = parse { lex(source) }
    syntaxErrors.forEach { println(it.render(source)) }

    val (initial, shapeErrors) = collecting { schema.decls.mapNotNull { s -> s.toDecl()?.let { s to it } } }
    shapeErrors.forEach { println(it.render(source)) }
    var pending = initial
    while (true) {
        val problems = validate(session + pending.map { it.second })
        val rejected = pending.filter { (_, decl) -> problems.diagnostics.any { it.kind.decl === decl } }
        if (rejected.isEmpty()) break
        problems.diagnostics
            .filter { p -> rejected.any { it.second === p.kind.decl } }
            .map { it.copy(span = pending.locate(it.kind)) }
            .forEach {
                val code = if (debug) "[${it.kind.code}] " else ""
                println("$code${it.render(source)}")
            }
        pending = pending - rejected.toSet()
    }
    session += pending.map { it.second }
    val accepted = SchemaSyntax(pending.map { it.first })
    println(if (debug) accepted else accepted.pretty())
} catch (e: IllegalArgumentException) {
    println("error:  ${e.message}")
}
