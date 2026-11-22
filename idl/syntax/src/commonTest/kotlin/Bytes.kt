package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyRange
import xyz.malefic.parz.idl.ShapeErrorKind.NegativeLength
import xyz.malefic.parz.idl.Type.Primitive.Bytes
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedDecimalBounds
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedInt
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.IntegerOutOfRange
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.InvalidType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parsing and printing `bytes`, `bytes(n)`, and `bytes(min..max)`.
 */
class Bytes {
    private fun assertRoundTrips(text: String) {
        val printed = typeOf(text).pretty()
        assertEquals(typeOf(text).toType(), typeOf(printed).toType(), "$text printed as $printed")
    }

    @Test
    fun forms() {
        assertEquals(Bytes(0L..Long.MAX_VALUE), typeOf("bytes").toType())
        assertEquals(Bytes(4L), typeOf("bytes(4)").toType())
        assertEquals(Bytes(0L), typeOf("bytes(0)").toType())
        assertEquals(Bytes(1L..5L), typeOf("bytes(1..5)").toType())
        assertEquals(Bytes(0L..5L), typeOf("bytes(0..5)").toType())
        assertEquals(Bytes(Long.MAX_VALUE), typeOf("bytes(${Long.MAX_VALUE})").toType())
    }

    @Test
    fun printsShortestForm() {
        assertEquals("bytes", typeOf("bytes").pretty())
        assertEquals("bytes(4)", typeOf("bytes(4)").pretty())
        assertEquals("bytes(4)", typeOf("bytes(4..4)").pretty())
        assertEquals("bytes(0)", typeOf("bytes(0..0)").pretty())
        assertEquals("bytes(1..5)", typeOf("bytes(1..5)").pretty())
        assertEquals("bytes(0..5)", typeOf("bytes(0..5)").pretty())
    }

    @Test
    fun roundTrips() = listOf("bytes", "bytes(0)", "bytes(4)", "bytes(0..0)", "bytes(1..5)", "bytes(3..3)").forEach(::assertRoundTrips)

    @Test
    fun spans() {
        listOf("bytes", "bytes(4)", "bytes(1..5)").forEach { text ->
            val source = "type T = $text"
            assertEquals(source.span(text), decl<DeclSyntax.Alias>(source).type.span, text)
        }
    }

    @Test
    fun insideContainers() =
        assertEquals("record A {\n  x: option<seq<bytes(2)>>,\n}", parsed("record A { x: option<seq<bytes(2)>> }").pretty())

    @Test
    fun emptyRange() {
        val source = "type T = bytes(5..2)"
        val error = error(source)
        assertEquals(InvalidType(EmptyRange(BigInteger(5), BigInteger(2))), error.kind)
        assertEquals("IDL124", error.kind.code)
        assertEquals(source.span("5"), error.span)
    }

    @Test
    fun negativeLengthsDoNotLex() {
        val source = "type T = bytes(-1)"
        assertEquals(ExpectedInt(Token.Symbol('-', source.span("-"))), error(source).kind)
    }

    @Test
    fun negativeLengthKind() = assertEquals("IDL127", InvalidType(NegativeLength(-1)).code)

    @Test
    fun tripleDotRange() {
        val source = "type T = bytes(1...5)"
        val error = error(source)
        assertEquals(ExpectedDecimalBounds::class, error.kind::class)
        assertEquals(source.span(".", 2), error.span)
    }

    @Test
    fun emptyParens() {
        val source = "type T = bytes()"
        assertEquals(ExpectedInt(Token.Symbol(')', source.span(")"))), error(source).kind)
    }

    @Test
    fun missingUpperBound() {
        val source = "type T = bytes(1..)"
        assertEquals(ExpectedInt(Token.Symbol(')', source.span(")"))), error(source).kind)
    }

    @Test
    fun lengthBeyondLong() {
        val source = "type T = bytes(99999999999999999999)"
        val error = error(source)
        assertEquals(IntegerOutOfRange(BigInteger.parseString("99999999999999999999"), "Long"), error.kind)
        assertEquals(source.span("99999999999999999999"), error.span)
    }

    @Test
    fun upperBoundBeyondLong() {
        val source = "type T = bytes(1..99999999999999999999)"
        assertEquals(IntegerOutOfRange(BigInteger.parseString("99999999999999999999"), "Long"), error(source).kind)
    }
}
