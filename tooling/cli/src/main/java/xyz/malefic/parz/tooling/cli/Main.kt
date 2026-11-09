package xyz.malefic.parz.tooling.cli

import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.core.sink
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.syntax.DeclSyntax.Companion.locate
import xyz.malefic.parz.idl.syntax.SchemaSyntax
import xyz.malefic.parz.idl.syntax.lex
import xyz.malefic.parz.idl.syntax.parse
import xyz.malefic.parz.idl.validate

fun main(vararg args: String) {
    val debug = args.contains("--debug") || args.contains("-d")
    for (entry in entries()) run(entry, debug)
}

private var session = emptyList<Decl>()

private fun run(
    source: String,
    debug: Boolean = false,
) {
    val (accepted, diagnostics) =
        collecting {
            val schema = parse(lex(source))
            var pending = schema.decls.mapNotNull { s -> s.toDecl()?.let { s to it } }
            while (true) {
                val problems = validate(session + pending.map { it.second })
                val rejected = pending.filter { (_, decl) -> problems.diagnostics.any { it.kind.decl === decl } }
                if (rejected.isEmpty()) break
                problems.diagnostics
                    .filter { p -> rejected.any { it.second === p.kind.decl } }
                    .forEach {
                        sink().report(it.copy(span = pending.locate(it.kind)))
                    }
                pending = pending - rejected.toSet()
            }
            session += pending.map { it.second }
            pending.map { it.first }
        }

    diagnostics.sortedWith(compareBy(nullsLast()) { it.span?.start }).forEach { println(it.render(source)) }
    println(SchemaSyntax(accepted).let { if (debug) it else it.pretty() })
    println()
}
