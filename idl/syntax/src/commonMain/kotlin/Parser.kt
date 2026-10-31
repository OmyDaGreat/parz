package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger

class Parser(
    private val tokens: List<Token>,
) {
    private var pos = 0

    val done get() = pos >= tokens.size

    fun accept(c: Char): Boolean {
        if (done) return false
        if (tokens[pos] is Token.Symbol && (tokens[pos] as Token.Symbol).char == c) {
            pos++
            return true
        }
        return false
    }

    fun expect(c: Char) {
        if (!accept(c)) throw SyntaxError("Expected '$c'", tokens.getOrNull(pos)?.offset ?: -1)
    }

    fun word(): String {
        if (done) throw SyntaxError("Expected word, but reached end of input", tokens.getOrNull(pos)?.offset ?: -1)
        val token = tokens[pos]
        if (token is Token.Word) {
            pos++
            return token.text
        } else {
            throw SyntaxError("Expected word, but got ${token::class.simpleName}", token.offset)
        }
    }

    fun int(): BigInteger {
        if (done) throw SyntaxError("Expected integer, but reached end of input", tokens.getOrNull(pos)?.offset ?: -1)
        val token = tokens[pos]
        if (token is Token.Number) {
            pos++
            return BigInteger.parseString(token.text)
        } else {
            throw SyntaxError("Expected integer, but got ${token::class.simpleName}", token.offset)
        }
    }

    fun <T> delimited(
        open: Char,
        close: Char,
        item: (Int) -> T,
    ): List<T> {
        expect(open)
        val result =
            buildList {
                while (!accept(close)) {
                    add(item(pos))
                    if (!accept(',')) {
                        expect(close)
                        break
                    }
                }
            }
        return result
    }

    fun <T> angled(inner: () -> T): T {
        expect('<')
        return inner().also { expect('>') }
    }
}
