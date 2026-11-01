package xyz.malefic.parz.idl.syntax

sealed interface Token {
    val offset: Int

    fun pretty(): String

    data class Word(
        val text: String,
        override val offset: Int,
    ) : Token {
        override fun pretty(): String = "'$text'"
    }

    data class Number(
        val text: String,
        override val offset: Int,
    ) : Token {
        override fun pretty(): String = text
    }

    data class Symbol(
        val char: Char,
        override val offset: Int,
    ) : Token {
        override fun pretty(): String = "'$char'"
    }

    data class EOF(
        override val offset: Int,
    ) : Token {
        override fun pretty(): String = "end of input"
    }
}

class SyntaxError(
    val reason: String,
    val offset: Int,
) : Exception("$reason at offset $offset") {
    fun describe(source: String): String {
        val before = source.substring(0..<offset.coerceAtMost(source.length))
        val line = before.count { it == '\n' } + 1
        val lineStart = before.lastIndexOf('\n') + 1
        val column = offset - lineStart + 1
        val text = source.substring(lineStart).substringBefore('\n')
        return "error: $reason at line $line:$column\n$text\n${" ".repeat(column - 1)}^"
    }
}

fun lex(source: String): List<Token> =
    buildList {
        var i = 0
        while (i < source.length) {
            val c = source[i]
            val start = i
            when {
                c.isWhitespace() -> {
                    i++
                }

                c.isLetter() || c == '_' -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    add(Token.Word(source.substring(start, i), start))
                }

                c in '0'..'9' -> {
                    while (i < source.length && source[i].isDigit()) i++
                    add(Token.Number(source.substring(start, i), start))
                }

                c in "(){}<>,;:=-." -> {
                    add(Token.Symbol(c, i++))
                }

                else -> {
                    throw SyntaxError("Unexpected character '$c'", i)
                }
            }
        }
        add(Token.EOF(source.trimEnd().length))
    }
