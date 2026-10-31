package xyz.malefic.parz.idl.syntax

sealed interface Token {
    val offset: Int

    data class Word(
        val text: String,
        override val offset: Int,
    ) : Token

    data class Number(
        val text: String,
        override val offset: Int,
    ) : Token

    data class Symbol(
        val char: Char,
        override val offset: Int,
    ) : Token
}

class SyntaxError(
    message: String,
    offset: Int,
) : Exception("$message at offset $offset")

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

                c.isDigit() -> {
                    while (i < source.length && source[i].isDigit()) i++
                    add(Token.Number(source.substring(start, i), start))
                }

                c in "(){}<>,;" -> {
                    add(Token.Symbol(c, i++))
                }

                else -> {
                    throw SyntaxError("Unexpected character '$c'", i)
                }
            }
        }
    }
