package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.EmptyPayload
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedInt
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedSymbol
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.IntegerOutOfRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class Variants {
    private val source = "variant Expr { lit(text: string, exact: bool), neg(inner: Expr) = 5, none }"
    private val expr = decl<DeclSyntax.Variant>(source)

    @Test
    fun declaration() {
        assertEquals("Expr", expr.name)
        assertEquals(source.span("variant Expr"), expr.span)
        assertFalse(expr.isEnum)
    }

    @Test
    fun membersAndTags() {
        assertEquals(listOf("lit", "neg", "none"), expr.members.map { it.name })
        assertEquals(listOf(0, 5, 6), expr.members.map { it.tag })
        assertEquals(listOf(null, source.span("5"), null), expr.members.map { it.tagSpan })
        assertEquals(listOf(source.span("lit"), source.span("neg"), source.span("none")), expr.members.map { it.span })
    }

    @Test
    fun memberFields() {
        val lit = expr.members[0]
        assertEquals(listOf("text", "exact"), lit.payload.map { it.name })
        assertEquals(listOf(source.span("text"), source.span("exact")), lit.payload.map { it.span })
        assertEquals(listOf(Type.Str(), Type.Bool), lit.payload.map { it.type.toType() })
        assertEquals(listOf(Type.Ref("Expr")), expr.members[1].payload.map { it.type.toType() })
        assertEquals(emptyList(), expr.members[2].payload)
    }

    @Test
    fun autoTagsContinueFromExplicitTags() =
        assertEquals(listOf(3, 4, 0, 1), decl<DeclSyntax.Variant>("variant V { a = 3, b, c = 0, d }").members.map { it.tag })

    @Test
    fun trailingComma() = assertEquals(1, decl<DeclSyntax.Variant>("variant V { a, }").members.size)

    @Test
    fun emptyVariantParses() = assertEquals(emptyList(), decl<DeclSyntax.Variant>("variant V {}").members)

    @Test
    fun emptyParentheses() {
        val source = "variant V { a(), b }"
        val error = error(source)
        assertEquals(EmptyPayload("a"), error.kind)
        assertEquals(source.span("(").start, error.span?.start)
    }

    @Test
    fun tagBeforeFields() {
        val source = "variant V { a = 1 (x: bool) }"
        assertEquals(ExpectedSymbol('}', Token.Symbol('(', source.span("("))), error(source).kind)
    }

    @Test
    fun negativeTag() {
        val source = "variant V { a = -1 }"
        assertEquals(ExpectedInt(Token.Symbol('-', source.span("-"))), error(source).kind)
    }

    @Test
    fun tagOutOfRange() {
        val source = "variant V { a = 99999999999 }"
        assertEquals(IntegerOutOfRange(BigInteger.parseString("99999999999"), "Int"), error(source).kind)
    }

    @Test
    fun missingComma() {
        val source = "variant V { a b }"
        assertEquals(ExpectedSymbol('}', Token.Word("b", source.span("b"))), error(source).kind)
    }

    @Test
    fun noTagSpanWithoutTag() = assertNull(decl<DeclSyntax.Variant>("variant V { a }").members.single().tagSpan)
}
