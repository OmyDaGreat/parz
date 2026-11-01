package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Decimal.Overflow
import xyz.malefic.parz.idl.Type.Decimal.Rounding

fun parse(tokens: List<Token>) = Parser(tokens).schema()

internal class Parser(
    private val tokens: List<Token>,
) {
    private var pos = 0

    private val current get() = tokens[pos]
    private val here get() = current.offset
    private val done get() = pos >= tokens.size

    fun schema() = SchemaSyntax(buildList { while (!done) add(decl()) })

    private fun type(): TypeExpr {
        val (word, offset) = word()
        return when (word) {
            "void" -> TypeExpr.Primitive(Type.Void, offset)
            "bool" -> TypeExpr.Primitive(Type.Bool, offset)
            "string" -> str(offset)
            "decimal" -> decimal(offset)
            "option" -> TypeExpr.Option(angled { type() }, offset)
            "seq" -> TypeExpr.Seq(angled { type() }, offset)
            "record", "enum" -> throw SyntaxError("Inline $word types aren't allowed", offset)
            else -> TypeExpr.Named(word, offset)
        }
    }

    private fun decl(): DeclSyntax {
        val (keyword, keywordOffset) = word()
        val (name, offset) = word()
        return when (keyword) {
            "record" -> DeclSyntax.Record(name, offset, fields())
            "enum" -> DeclSyntax.Enum(name, offset, members())
            "type" -> expect('=').let { DeclSyntax.Alias(name, offset, type()) }
            else -> throw SyntaxError("Expected 'record', 'enum', or 'type', but got '$keyword'", keywordOffset)
        }
    }

    private fun accept(c: Char): Boolean {
        val token = current
        if (token is Token.Symbol && token.char == c) {
            pos++
            return true
        }
        return false
    }

    private fun expect(c: Char) {
        if (!accept(c)) throw SyntaxError("Expected '$c', but got ${current.pretty()}", here)
    }

    private fun word(): Token.Word {
        val token = current as? Token.Word ?: throw SyntaxError("Expected word, but got ${current.pretty()}", here)
        pos++
        return token
    }

    private fun int(): BigInteger {
        val token = current as? Token.Number ?: throw SyntaxError("Expected integer, but got ${current.pretty()}", here)
        pos++
        return BigInteger.parseString(token.text)
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
        TypeExpr.Primitive(
            checkSyntax("string", offset) {
                if (accept('(')) Type.Str(int().exactLong().also { expect(')') }) else Type.Str()
            },
            offset,
        )

    private fun fields(): List<FieldSyntax> = delimited('{', '}') { field() }

    private fun members(): List<MemberSyntax> {
        expect('{')
        var lastTag = -1
        return buildList {
            while (!accept('}')) {
                val member = member(++lastTag)
                add(member)
                lastTag = member.tag
                if (!accept(',')) {
                    expect('}')
                    break
                }
            }
        }
    }

    private fun field(): FieldSyntax {
        val (name, offset) = word()
        expect(':')
        return FieldSyntax(name, offset, type())
    }

    private fun member(autoTag: Int): MemberSyntax {
        val (name, offset) = word()
        if (!accept('=')) return MemberSyntax(name, offset, autoTag, null)
        return MemberSyntax(name, offset, int().exactInt(), here)
    }

    private fun <T> angled(inner: () -> T): T {
        expect('<')
        return inner().also { expect('>') }
    }

    private fun decimal(offset: Int): TypeExpr.Primitive {
        expect('(')
        var signum = 1
        if (accept('-')) {
            signum = -1
        }
        val precisionOrMin = int() * signum
        return TypeExpr.Primitive(
            checkSyntax("decimal", offset) {
                if (accept(',')) {
                    val scale = int()
                    val rounding = if (accept(',')) enumWord<Rounding>(offset) else Rounding.TOWARDS_ZERO
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
            },
            offset,
        )
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
