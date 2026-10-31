@file:Suppress("ktlint:standard:no-empty-file")

package xyz.malefic.parz.idl.syntax

/*

import xyz.malefic.parz.idl.Value

private class Parser(
    private val tokens: List<String>,
) {
    private var pos = 0

    val done get() = pos >= tokens.size

    fun value(): Value =
        when (val token = tokens.getOrNull(pos++) ?: error("Unexpected end of input")) {
            "true" -> Value.Bool(true)
            "false" -> Value.Bool(false)
            "void" -> Value.Void
            "none" -> Value.None
            "some" -> Value.Some(value())
            else -> error("Unsupported/unknown value '$token' at pos $pos.") // TODO: Support more values
        }
}

fun parse(string: String): List<Value> {
    val parser = Parser(string.split(Regex("\\s+")).filter { it.isNotBlank() })
    return buildList { while (!parser.done) add(parser.value()) }
}

*/
