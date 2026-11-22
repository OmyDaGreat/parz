package xyz.malefic.parz.tooling.cli

import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.syntax.DeclSyntax
import xyz.malefic.parz.idl.syntax.SchemaSyntax
import xyz.malefic.parz.idl.syntax.accept
import xyz.malefic.parz.idl.syntax.lex
import xyz.malefic.parz.idl.syntax.parse

class Session {
    val decls = linkedMapOf<String, Pair<DeclSyntax, Decl>>()
    var debug = false
    var running = true

    fun run(source: String) {
        val (accepted, diagnostics) =
            collecting {
                val accepted = parse(lex(source)).accept(decls.values.map { it.second })
                accepted.forEach { decls[it.second.name] = it }
                accepted.map { it.first }
            }

        val output = if (debug) SchemaSyntax(accepted).toString() else SchemaSyntax(accepted).pretty()
        val blocks =
            diagnostics.sortedWith(compareBy(nullsLast()) { it.span?.start }).map { it.render(source) } +
                listOf(output).filter { it.isNotEmpty() }
        if (blocks.isNotEmpty()) println(blocks.joinToString("\n\n"))
    }
}
