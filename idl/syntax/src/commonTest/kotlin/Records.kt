package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Type.Collection.Option
import xyz.malefic.parz.idl.Type.Primitive.Decimal
import xyz.malefic.parz.idl.Type.Primitive.Str
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedSymbol
import kotlin.test.Test
import kotlin.test.assertEquals

class Records {
    @Test
    fun fieldsAndSpans() {
        val source = "record Payment { amount: decimal(9, 2), note: option<string> }"
        val record = decl<DeclSyntax.Record>(source)
        assertEquals("Payment", record.name)
        assertEquals(source.span("record Payment"), record.span)
        assertEquals(listOf("amount", "note"), record.fields.map { it.name })
        assertEquals(listOf(source.span("amount"), source.span("note")), record.fields.map { it.span })
        assertEquals(
            listOf(Decimal(BigInteger(-999999999), BigInteger(999999999), 2), Option(Str())),
            record.fields.map { it.type.toType() },
        )
    }

    @Test
    fun trailingComma() = assertEquals(1, decl<DeclSyntax.Record>("record A { x: bool, }").fields.size)

    @Test
    fun emptyRecordParses() = assertEquals(emptyList(), decl<DeclSyntax.Record>("record A {}").fields)

    @Test
    fun keywordsAsFieldNames() =
        assertEquals(
            listOf(
                "type",
                "record",
                "enum",
            ),
            decl<DeclSyntax.Record>("record A { type: bool, record: bool, enum: bool }").fields.map {
                it.name
            },
        )

    @Test
    fun missingColon() {
        val source = "record A { x bool }"
        assertEquals(ExpectedSymbol(':', Token.Word("bool", source.span("bool"))), error(source).kind)
    }

    @Test
    fun missingComma() {
        val source = "record A { x: bool y: bool }"
        assertEquals(ExpectedSymbol('}', Token.Word("y", source.span("y"))), error(source).kind)
    }

    @Test
    fun unclosed() {
        val source = "record A { x: bool"
        assertEquals(ExpectedSymbol('}', Token.EOF(source.length)), error(source).kind)
    }
}
