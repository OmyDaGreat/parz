package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type

class Parser(
    private val tokens: List<Token>,
) {
    private var pos = 0

    val done get() = pos >= tokens.size

    fun type(): Type {
        val (word, offset) = word()
        return when (word) {
            "void" -> Type.Void
            "bool" -> Type.Bool
            "string" -> if (accept('(')) Type.Str(int().longValue().also { accept(')') }) else Type.Str()
            "option" -> Type.Option(angled { type() })
            "seq" -> Type.Seq(angled { type() })
            "decimal" -> decimal()
            "record" -> Type.Record(delimited('{', '}', { field() }))
            "enum" -> Type.Enum(delimited('{', '}', ::member))
            else -> throw SyntaxError("unknown type '$word'", offset)
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
        pos++
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

    private fun <T> angled(inner: () -> T): T {
        expect('<')
        return inner().also { expect('>') }
    }

    private fun decimal(): Type.Decimal {
        expect('(')
        val precision = int()
        expect(',')
        val scaleOrMin = int()
        return if (accept(',')) {
            val rounding = enumWord<Type.Decimal.Rounding>()
            val overflow = if (accept(',')) enumWord<Type.Decimal.Overflow>() else Type.Decimal.Overflow.ERROR
            expect(')')
            val bound = BigInteger.TEN.pow(precision) - BigInteger.ONE
            Type.Decimal(-bound, bound, scaleOrMin.intValue(), rounding, overflow)
        } else if (accept('.') && accept('.') && accept('.')) {
            val max = int()
            val rounding = if (accept(',')) enumWord<Type.Decimal.Rounding>() else Type.Decimal.Rounding.TOWARDS_ZERO
            val overflow = if (accept(',')) enumWord<Type.Decimal.Overflow>() else Type.Decimal.Overflow.ERROR
            expect(')')
            Type.Decimal(scaleOrMin, max, scaleOrMin.intValue(), rounding, overflow)
        } else {
            val rounding = Type.Decimal.Rounding.TOWARDS_ZERO
            val overflow = Type.Decimal.Overflow.ERROR
            expect(')')
            val bound = BigInteger.TEN.pow(precision) - BigInteger.ONE
            Type.Decimal(-bound, bound, scaleOrMin.intValue(), rounding, overflow)
        }
    }

    private fun field(): Type.Record.Field {
        val (name, _) = word()
        expect(':')
        return Type.Record.Field(name, type())
    }

    private fun member(index: Int): Type.Enum.Member {
        val (name, _) = word()
        expect(':')
        return Type.Enum.Member(name, index)
    }
}
