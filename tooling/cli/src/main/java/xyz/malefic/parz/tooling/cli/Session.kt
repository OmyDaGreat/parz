package xyz.malefic.parz.tooling.cli

import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.core.sink
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.syntax.DeclSyntax
import xyz.malefic.parz.idl.syntax.DeclSyntax.Companion.locate
import xyz.malefic.parz.idl.syntax.SchemaSyntax
import xyz.malefic.parz.idl.syntax.lex
import xyz.malefic.parz.idl.syntax.parse
import xyz.malefic.parz.idl.validate
import kotlin.collections.plus

class Session {
    val decls = linkedMapOf<String, Pair<DeclSyntax, Decl>>()
    var debug = false
    var running = true

    fun run(source: String) {
        val (accepted, diagnostics) =
            collecting {
                val schema = parse(lex(source))
                var pending = schema.decls.mapNotNull { s -> s.toDecl()?.let { s to it } }
                while (true) {
                    val problems = validate(decls.values.map { it.second } + pending.map { it.second })
                    val rejected = pending.filter { (_, decl) -> problems.diagnostics.any { it.kind.decl === decl } }
                    if (rejected.isEmpty()) break
                    problems.diagnostics
                        .filter { p -> rejected.any { it.second === p.kind.decl } }
                        .forEach {
                            sink().report(it.copy(span = pending.locate(it.kind)))
                        }
                    pending = pending - rejected.toSet()
                }
                pending.forEach { decls[it.second.name] = it }
                pending.map { it.first }
            }

        diagnostics.sortedWith(compareBy(nullsLast()) { it.span?.start }).forEach { println(it.render(source)) }
        val output = if (debug) SchemaSyntax(accepted).toString() else SchemaSyntax(accepted).pretty()
        val blocks =
            diagnostics.sortedWith(compareBy(nullsLast()) { it.span?.start }).map { it.render(source) } +
                listOf(output).filter { it.isNotEmpty() }
        if (blocks.isNotEmpty()) println(blocks.joinToString("\n\n"))
    }
}
