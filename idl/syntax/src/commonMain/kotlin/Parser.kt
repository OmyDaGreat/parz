package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Decimal.Overflow
import xyz.malefic.parz.idl.Type.Decimal.Rounding

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
            "void" -> Type.Void
            "bool" -> Type.Bool
            "string" -> str(offset)
            "option" -> Type.Option(angled { type() })
            "seq" -> Type.Seq(angled { type() })
            "decimal" -> decimal(offset)
            else -> Type.Ref(word)
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

    private fun word(): Token.Word {
        if (done) throw SyntaxError("Expected word, but reached end of input", tokens.getOrNull(pos)?.offset ?: -1)
        val token = tokens[pos]
        if (token is Token.Word) {
            pos++
            return token
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

    private inline fun <reified T : Enum<T>> enumWord(initialOffset: Int): T {
        val (word, _) = word()
        return enumValues<T>().find { it.name.equals(word, ignoreCase = true) }
            ?: throw SyntaxError(
                "Expected one of ${enumValues<T>().joinToString(", ") { it.name }}, but got '$word'",
                initialOffset,
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

    private fun str(offset: Int) =
        checkSyntax("string", offset) {
            if (accept('(')) Type.Str(int().exactLong().also { expect(')') }) else Type.Str()
        }

    // TODO: Should be used by the record/enum declaration parsing to replace the inline record and enum types (now nonexistent)
    private fun fields(): List<Decl.Record.Field> = delimited('{', '}') { field() }

    private fun members(): List<Decl.Enum.Member> {
        expect('{')
        var lastTag = -1
        return buildList {
            while (!accept('}')) {
                add(member(++lastTag))
                if (accept('=')) {
                    val tag = int().exactInt()
                    lastTag = tag
                    this[lastIndex] = last().copy(tag = tag)
                }
                if (!accept(',')) {
                    expect('}')
                    break
                }
            }
        }
    }

    private fun field(): Decl.Record.Field {
        val (name, index) = word()
        expect(':')
        return checkSyntax("record field", index) { Decl.Record.Field(name, type()) }
    }

    private fun member(tag: Int): Decl.Enum.Member {
        val (name, index) = word()
        return checkSyntax("enum member", index) { Decl.Enum.Member(name, tag) }
    }

    private fun <T> angled(inner: () -> T): T {
        expect('<')
        return inner().also { expect('>') }
    }

    private fun decimal(offset: Int): Type.Decimal {
        expect('(')
        var signum = 1
        if (accept('-')) {
            signum = -1
        }
        val precisionOrMin = int() * signum
        return checkSyntax("decimal", offset) {
            if (accept(',')) {
                val scale = int()
                expect(',')
                val rounding = enumWord<Rounding>(offset)
                val overflow = if (accept(',')) enumWord<Overflow>(offset) else Overflow.ERROR
                expect(')')
                val bound = BigInteger.TEN.pow(precisionOrMin) - BigInteger.ONE
                Type.Decimal(-bound, bound, scale.exactInt(), rounding, overflow)
            } else if (accept('.') && accept('.') && accept('.')) {
                signum = if (accept('-')) -1 else 1
                val max = int() * signum
                expect(',')
                val scale = int()
                val rounding = if (accept(',')) enumWord<Rounding>(offset) else Rounding.TOWARDS_ZERO
                val overflow = if (accept(',')) enumWord<Overflow>(offset) else Overflow.ERROR
                expect(')')
                Type.Decimal(precisionOrMin, max, scale.exactInt(), rounding, overflow)
            } else {
                throw SyntaxError("Expected ',' or '...' in decimal type after first int", offset)
            }
        }
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

    private fun BigInteger.exactInt(): Int =
        try {
            intValue(exactRequired = true)
        } catch (_: ArithmeticException) {
            throw SyntaxError("Integer value $this is out of range for Int", pos - 1)
        }

    private fun BigInteger.exactLong(): Long =
        try {
            longValue(exactRequired = true)
        } catch (_: ArithmeticException) {
            throw SyntaxError("Integer value $this is out of range for Long", pos - 1)
        }
}
