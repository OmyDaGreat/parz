package xyz.malefic.parz.tooling.cli

fun main(vararg args: String) {
    val session = Session().apply { debug = "--debug" in args || "-d" in args }
    for (entry in entries()) {
        if (entry.trimStart().startsWith("#")) session.directive(entry) else session.run(entry)
        if (!session.running) break
    }
}
