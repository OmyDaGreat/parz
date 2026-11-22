package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant.Member
import xyz.malefic.parz.idl.model.SchemaErrorKind
import xyz.malefic.parz.idl.model.SchemaErrorKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.model.ShapeErrorKind
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every diagnostic kind has its own stable `IDLnnn` code.
 */
class Codes {
    private val token = Token.EOF(0)
    private val decl = Decl.Alias("A", Bool)
    private val member = Member("m", 0)

    private val syntax =
        listOf(
            SyntaxErrorKind.UnexpectedCharacter('x'),
            SyntaxErrorKind.InlineDecl("record"),
            SyntaxErrorKind.ExpectedDeclKeyword(token),
            SyntaxErrorKind.ExpectedSymbol('x', token),
            SyntaxErrorKind.ExpectedWord(token),
            SyntaxErrorKind.ExpectedInt(token),
            SyntaxErrorKind.ExpectedOption(emptyList(), ""),
            SyntaxErrorKind.PrecisionOutOfRange(BigInteger.ZERO),
            SyntaxErrorKind.ExpectedDecimalBounds(token),
            SyntaxErrorKind.IntegerOutOfRange(BigInteger.ZERO, "Int"),
            SyntaxErrorKind.EmptyPayload("m"),
        )

    private val shape =
        listOf(
            ShapeErrorKind.EmptyRecord,
            ShapeErrorKind.EmptyVariant,
            ShapeErrorKind.EmptyName,
            ShapeErrorKind.NegativeTag(-1),
            ShapeErrorKind.EmptyRange(BigInteger.ONE, BigInteger.ZERO),
            ShapeErrorKind.NonPositiveLength(0),
            ShapeErrorKind.EnumPayload(member),
        )

    private val schema =
        listOf(
            SchemaErrorKind.UnknownType("X", decl, null),
            SchemaErrorKind.DuplicateFieldName(RecordPath(Field("f", Bool)), decl),
            SchemaErrorKind.MemberError.DuplicateMemberName("m", decl, member),
            SchemaErrorKind.MemberError.DuplicateMemberTag(0, decl, member),
            SchemaErrorKind.CyclicType(emptyList(), decl),
            SchemaErrorKind.DuplicateDecl(decl),
        )

    @Test
    fun codesAreUnique() {
        val codes = (syntax + shape + schema).map { it.code }
        assertEquals(codes, codes.distinct(), "duplicate codes: ${codes.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test
    fun codesAreWellFormed() = (syntax + shape + schema).forEach { assertTrue(Regex("IDL\\d{3}").matches(it.code), it.code) }

    @Test
    fun releasedCodesAreStable() {
        assertEquals(
            listOf("IDL120", "IDL121", "IDL122", "IDL123", "IDL124", "IDL125", "IDL126"),
            shape.map { it.code },
        )
        assertEquals(listOf("IDL101", "IDL102", "IDL103", "IDL104", "IDL105", "IDL106"), schema.map { it.code })
    }

    @Test
    fun invalidTypeUsesWrappedCode() {
        val wrapped = ShapeErrorKind.NonPositiveLength(0)
        assertEquals(wrapped.code, SyntaxErrorKind.InvalidType(wrapped).code)
        assertEquals(wrapped.message, SyntaxErrorKind.InvalidType(wrapped).message)
    }
}
