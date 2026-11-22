package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedSymbol
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedWord
import kotlin.test.Test
import kotlin.test.assertEquals

class Aliases {
    @Test
    fun alias() {
        val source = "type Payments = seq<Payment>"
        val alias = decl<DeclSyntax.Alias>(source)
        assertEquals("Payments", alias.name)
        assertEquals(source.span("type Payments"), alias.span)
        assertEquals(Seq(Type.Ref("Payment")), alias.type.toType())
    }

    @Test
    fun missingEquals() {
        val source = "type Money decimal(9, 2)"
        assertEquals(ExpectedSymbol('=', Token.Word("decimal", source.span("decimal"))), error(source).kind)
    }

    @Test
    fun missingType() {
        val source = "type Money ="
        assertEquals(ExpectedWord(Token.EOF(source.length)), error(source).kind)
    }
}
