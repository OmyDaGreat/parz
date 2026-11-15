package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedDeclKeyword
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedSymbol
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.ExpectedWord
import xyz.malefic.parz.idl.syntax.SyntaxErrorKind.UnexpectedCharacter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * After a syntax error, the parser skips to the next declaration and keeps going.
 */
class Recovery {
    private fun names(source: String) = parseSource(source).value.decls.map { it.name }

    private fun codes(source: String) = errors(source).map { it.kind.code }

    @Test
    fun empty() {
        assertEquals(SchemaSyntax(emptyList()), parsed(""))
        assertEquals(SchemaSyntax(emptyList()), parsed("  \n\t "))
    }

    @Test
    fun skipsBrokenDecl() {
        val source = "record A { x }\nrecord B { y: bool }"
        assertEquals(listOf("B"), names(source))
        assertEquals(ExpectedSymbol(':', Token.Symbol('}', source.span("}"))), error(source).kind)
    }

    @Test
    fun unknownKeyword() {
        val source = "struct A { x: bool }\nrecord B { y: bool }"
        assertEquals(listOf("B"), names(source))
        assertEquals(ExpectedDeclKeyword(Token.Word("struct", source.span("struct"))), error(source).kind)
    }

    @Test
    fun garbageBeforeFirstDecl() {
        val source = "}}} record B { y: bool }"
        assertEquals(listOf("B"), names(source))
        assertEquals(ExpectedWord(Token.Symbol('}', source.span("}"))), error(source).kind)
    }

    @Test
    fun keywordFieldNameIsNotADeclStart() {
        val source = "record A { x: , type: bool }\nrecord B { y: bool }"
        assertEquals(listOf("B"), names(source))
        assertEquals(listOf("IDL005"), codes(source))
    }

    @Test
    fun oneErrorPerBrokenDecl() {
        val source = "record A { x }\nrecord B { y: bool }\nenum C { a = }\ntype D = bool"
        assertEquals(listOf("B", "D"), names(source))
        assertEquals(listOf("IDL004", "IDL006"), codes(source))
    }

    @Test
    fun lexErrorsDoNotStopParsing() {
        val source = "record A { x: bool @ }"
        assertEquals(listOf("A"), names(source))
        assertEquals(listOf(UnexpectedCharacter('@')), errors(source).map { it.kind })
    }

    @Test
    fun truncatedDecl() {
        assertEquals(listOf("IDL005"), codes("record"))
        assertEquals(emptyList(), names("record"))
    }
}
