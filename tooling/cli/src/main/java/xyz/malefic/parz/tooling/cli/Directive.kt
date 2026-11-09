package xyz.malefic.parz.tooling.cli

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class Directive(
    val name: String,
    val usage: String,
    val help: String,
    val action: Session.(args: List<String>) -> Unit,
)

val directives: Map<String, Directive> =
    listOf(
        Directive("help", "#help", "Show this list") {
            directives.values.forEach { println("  ${it.usage.padEnd(16)} ${it.help}") }
        },
        Directive("list", "#list", "List declarations in the session") {
            decls.keys.forEach(::println)
        },
        Directive("show", "#show <name>", "Print a declaration") { args ->
            val name = args.singleOrNull() ?: return@Directive println("usage: #show <name>")
            decls[name]?.let { println(it.first.pretty()) } ?: println("No declaration named $name")
        },
        Directive("reset", "#reset", "Clear the session") { decls.clear() },
        Directive("debug", "#debug", "Toggle debug output") {
            debug = !debug
            println("debug ${if (debug) "on" else "off"}")
        },
        Directive("load", "#load <file>", "Run a file as an entry") { args ->
            val path = Path(args.singleOrNull() ?: return@Directive println("usage: #load <file>"))
            if (path.exists()) run(path.readText()) else println("File not found: $path")
        },
        Directive("quit", "#quit", "Exit") { running = false },
    ).associateBy { it.name }

fun Session.directive(line: String) {
    val words =
        line
            .trim()
            .removePrefix("#")
            .removeSuffix(";;")
            .split("\\s+".toRegex())
    val d = directives[words.first()] ?: return println("Unknown directive #${words.first()}. Try #help")
    d.action(this, words.drop(1))
}
