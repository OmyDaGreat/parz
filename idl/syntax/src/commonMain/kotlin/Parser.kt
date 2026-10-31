package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type

fun parse(tokens: List<Token>): List<Type> {
    val parser = Parser(tokens)
    return buildList {
        while (!parser.done) {
            add(parser.type())
        }
    }
}

internal class Parser(
    private val tokens: List<Token>,
) {
    private var pos = 0

    val done get() = pos >= tokens.size

    fun type(): Type {
        val (word, offset) = word()
        return when (word) {
            "void" -> {
                Type.Void
            }

            "bool" -> {
                Type.Bool
            }

            "string" -> {
                checkSyntax("string", pos) {
                    if (accept('(')) Type.Str(int().longValue(exactRequired = true).also { expect(')') }) else Type.Str()
                }
            }

            "option" -> {
                Type.Option(angled { type() })
            }

            "seq" -> {
                Type.Seq(angled { type() })
            }

            "decimal" -> {
                decimal()
            }

            "record" -> {
                checkSyntax("record", pos) { Type.Record(delimited('{', '}') { field() }) }
            }

            "enum" -> {
                enum()
            }

            else -> {
                Type.Ref(word)
            }
        }
    }

    private fun accept(c: Char): Boolean {
        if (done) return false
        val token = tokens[pos]
        if (token is Token.Symbol && token.char == c) {
            pos++
            return true
        }
        return false
    }

    private fun expect(c: Char) {
        if (!accept(c)) throw SyntaxError("Expected '$c'", tokens.getOrNull(pos)?.offset ?: -1)
    }

    private fun word(): Pair<String, Int> {
        if (done) throw SyntaxError("Expected word, but reached end of input", tokens.getOrNull(pos)?.offset ?: -1)
        val token = tokens[pos]
        if (token is Token.Word) {
            pos++
            return token.text to pos
        } else {
            throw SyntaxError("Expected word, but got ${token::class.simpleName}", token.offset)
        }
    }

    private fun int(): BigInteger {
        if (done) throw SyntaxError("Expected integer, but reached end of input", tokens.getOrNull(pos)?.offset ?: -1)
        val token = tokens[pos]
        if (token is Token.Number) {
            pos++
            return BigInteger.parseString(token.text)
        } else {
            throw SyntaxError("Expected integer, but got ${token::class.simpleName}", token.offset)
        }
    }

    private inline fun <reified T : Enum<T>> enumWord(): T {
        val (word, _) = word()
        return enumValues<T>().find { it.name.equals(word, ignoreCase = true) }
            ?: throw SyntaxError(
                "Expected one of ${enumValues<T>().joinToString(", ") { it.name }}, but got '$word'",
                pos - 1,
            )
    }

    private fun <T> delimited(
        open: Char,
        close: Char,
        item: (Int) -> T,
    ): List<T> {
        expect(open)
        val result =
            buildList {
                while (!accept(close)) {
                    add(item(size))
                    if (!accept(',')) {
                        expect(close)
                        break
                    }
                }
            }
        return result
    }

    private fun enum(): Type.Enum {
        expect('{')
        var lastTag = -1
        val result =
            buildList<Type.Enum.Member> {
                while (!accept('}')) {
                    add(member(++lastTag))
                    if (accept('=')) {
                        val tag = int().intValue(exactRequired = true)
                        lastTag = tag
                        this[lastIndex] = last().copy(tag = tag)
                    }
                    if (!accept(',')) {
                        expect('}')
                        break
                    }
                }
            }
        return checkSyntax("enum", pos) { Type.Enum(result) }
    }

    private fun <T> angled(inner: () -> T): T {
        expect('<')
        return inner().also { expect('>') }
    }

    private fun decimal(): Type.Decimal {
        expect('(')
        var signum = 1
        if (accept('-')) {
            signum = -1
        }
        val precisionOrMin = int() * signum
        return checkSyntax("decimal", pos) {
            if (accept(',')) {
                val scale = int()
                expect(',')
                val rounding = enumWord<Type.Decimal.Rounding>()
                val overflow = if (accept(',')) enumWord<Type.Decimal.Overflow>() else Type.Decimal.Overflow.ERROR
                expect(')')
                val bound = BigInteger.TEN.pow(precisionOrMin) - BigInteger.ONE
                Type.Decimal(-bound, bound, scale.intValue(exactRequired = true), rounding, overflow)
            } else if (accept('.') && accept('.') && accept('.')) {
                signum = if (accept('-')) -1 else 1
                val max = int() * signum
                expect(',')
                val scale = int()
                val rounding = if (accept(',')) enumWord<Type.Decimal.Rounding>() else Type.Decimal.Rounding.TOWARDS_ZERO
                val overflow = if (accept(',')) enumWord<Type.Decimal.Overflow>() else Type.Decimal.Overflow.ERROR
                expect(')')
                Type.Decimal(precisionOrMin, max, scale.intValue(exactRequired = true), rounding, overflow)
            } else {
                throw SyntaxError("Expected ',' or '...' in decimal type after first int", pos)
            }
        }
    }

    private fun field(): Type.Record.Field {
        val (name, index) = word()
        expect(':')
        return checkSyntax("record field", index) { Type.Record.Field(name, type()) }
    }

    private fun member(tag: Int): Type.Enum.Member {
        val (name, index) = word()
        return checkSyntax("enum member", index) { Type.Enum.Member(name, tag) }
    }

    private fun <T> checkSyntax(
        name: String,
        index: Int,
        type: () -> T,
    ): T =
        try {
            type()
        } catch (e: IllegalArgumentException) {
            throw SyntaxError(e.message ?: "Invalid $name", index)
        }
}
