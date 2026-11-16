package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.encoding.CodecErrorKind.ExpectedOneValue
import xyz.malefic.parz.idl.encoding.CodecErrorKind.MissingHeader
import xyz.malefic.parz.idl.encoding.CodecErrorKind.SchemaMismatch
import xyz.malefic.parz.idl.encoding.CodecErrorKind.UnknownRootType
import xyz.malefic.parz.idl.encoding.CodecErrorKind.UnsupportedVersion
import xyz.malefic.parz.idl.encoding.CodecErrorKind.WrongRootType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Files: `(parz <format version> (schema <name> <version>) <Root>)` followed by the body.
 */
class Headers {
    private val id = SchemaId("shop", 1)

    private val header = "(parz 1 (schema shop 1) Line)"

    private val body = "((amount 12.34) (note none))"

    private fun file(
        header: String = this.header,
        body: String = this.body,
    ) = "$header\n$body\n"

    /**
     * The single error reading [text]'s header, underlined at [at] (or with no span if [at] is null).
     */
    private fun headerError(
        text: String,
        at: String?,
    ): AnyDiagnostic {
        val (value, diagnostics) = readFile(text, shop, id)
        assertNull(value, "expected no file reading:\n$text")
        assertEquals(1, diagnostics.size, "expected one error reading:\n$text\ngot $diagnostics")
        assertEquals(at?.let { text.span(it) }, diagnostics.single().span, "span reading:\n$text")
        return diagnostics.single()
    }

    private fun decodedFile(
        text: String,
        expected: Type.Ref? = null,
    ): Value {
        val (value, diagnostics) = decodeFile(text, shop, id, expected)
        assertEquals(emptyList(), diagnostics, "unexpected errors decoding:\n$text")
        return value!!
    }

    @Test fun encodedFileText() = assertEquals(file(), encodeFile(lineValue(1234), line, shop, id))

    @Test fun headerStaysOnOneLineWhenTheBodyWraps() {
        val text = encodeFile(lineValue(1234, "a note"), line, shop, id, targetWidth = 10)
        assertEquals(header, text.lines().first())
        assertEquals(true, text.lines().size > 3, "body should wrap at width 10:\n$text")
        assertEquals(lineValue(1234, "a note"), decodedFile(text))
    }

    @Test fun roundTrip() {
        listOf(
            lineValue(-5, "x") to line,
            pending to status,
            failed("card", 3) to status,
            Value.Record(mapOf("lines" to Value.Seq(listOf(lineValue(100))), "status" to pending)) to order,
            dec(-5, 2) to amount,
        ).forEach { (value, root) ->
            assertEquals(value, decodedFile(encodeFile(value, root, shop, id)), "$value as $root")
            assertEquals(value, decodedFile(encodeFile(value, root, shop, id), expected = root), "$value as $root, expected")
        }
    }

    @Test fun withoutAnExpectedRootAnyDeclaredRootIsAccepted() {
        assertEquals(lineValue(1234), decodedFile(file()))
        assertEquals(pending, decodedFile(file("(parz 1 (schema shop 1) Status)", "pending")))
    }

    @Test fun wrongRootType() {
        val text = file()
        val (value, diagnostics) = decodeFile(text, shop, id, expected = order)
        assertNull(value)
        assertEquals(listOf(WrongRootType("Order", "Line")), diagnostics.map { it.kind })
        assertEquals(text.span("Line"), diagnostics.single().span)
    }

    @Test fun readFileKeepsTheRawBody() {
        val (file, diagnostics) = readFile(file(body = "a (b c) \"d\""), shop, id)
        assertEquals(emptyList(), diagnostics)
        assertEquals(line, file!!.root)
        assertEquals(listOf(atom("a"), list(atom("b"), atom("c")), str("d")), file.body.withoutSpans())
    }

    @Test fun readFileAllowsAnEmptyBody() = assertEquals(emptyList(), readFile(header, shop, id).value!!.body)

    @Test fun bodyMustBeExactlyOneValue() {
        assertEquals(listOf(ExpectedOneValue(0)), decodeFile(header, shop, id).diagnostics.map { it.kind })
        assertEquals(listOf(ExpectedOneValue(2)), decodeFile(file(body = "$body $body"), shop, id).diagnostics.map { it.kind })
    }

    @Test fun emptyInputHasNoHeader() = assertEquals(MissingHeader, headerError("", at = null).kind)

    @Test fun bareValueHasNoHeader() = assertEquals(MissingHeader, headerError(body, at = body).kind)

    @Test fun otherHeadsAreNotHeaders() {
        assertEquals(MissingHeader, headerError("(json 1 (schema shop 1) Line)", at = "(json 1 (schema shop 1) Line)").kind)
        assertEquals(MissingHeader, headerError("parz", at = "parz").kind)
    }

    @Test fun unsupportedFormatVersion() {
        listOf("2", "0", "01", "1.0", "x").forEach { version ->
            val text = "(parz $version (schema shop 1) Line)"
            assertEquals(UnsupportedVersion(version), headerError(text, at = version).kind, text)
        }
    }

    @Test fun unsupportedVersionMessage() = assertEquals("Unsupported format version '2'", UnsupportedVersion("2").message)

    @Test fun schemaMismatch() {
        listOf("(schema other 1)", "(schema shop 2)", "(schema shop)", "(schema shop 1 x)", "(scheme shop 1)").forEach { given ->
            val text = "(parz 1 $given Line)"
            assertEquals(SchemaMismatch(id, given), headerError(text, at = given).kind, text)
        }
    }

    @Test fun schemaMismatchMessage() =
        assertEquals("Schema mismatch: expected 'shop 1', found '(schema shop 2)'", SchemaMismatch(id, "(schema shop 2)").message)

    @Test fun unknownRootType() = assertEquals(UnknownRootType("Nope"), headerError("(parz 1 (schema shop 1) Nope)", at = "Nope").kind)

    @Test fun malformedHeaders() {
        listOf(
            "(parz)" to "(parz)",
            "(parz 1)" to "(parz 1)",
            "(parz 1 (schema shop 1))" to "(parz 1 (schema shop 1))",
            "(parz 1 (schema shop 1) (Line))" to "(Line)",
            "(parz 1 (schema shop 1) \"Line\")" to "\"Line\"",
            "(parz 1 (schema shop 1) Line extra)" to "extra",
            "(parz (1) (schema shop 1) Line)" to "(1)",
            "(parz \"1\" (schema shop 1) Line)" to "\"1\"",
            "(parz 1 schema Line)" to "schema",
            "(parz 1 \"shop\" Line)" to "\"shop\"",
        ).forEach { (text, at) -> assertIs<CodecErrorKind.Expected>(headerError(text, at).kind, text) }
    }

    @Test fun headerErrorsStopBeforeTheBody() {
        val (value, diagnostics) = decodeFile(file("(parz 2 (schema shop 1) Line)", "((amount x))"), shop, id)
        assertNull(value)
        assertEquals(listOf("IDL218"), codes(diagnostics))
    }

    @Test fun bodyErrorsPointIntoTheFile() {
        val text = file(body = "((amount 1.505) (note none))")
        val (value, diagnostics) = decodeFile(text, shop, id)
        assertNull(value)
        assertEquals(listOf("IDL215"), codes(diagnostics))
        assertEquals(text.span("1.505"), diagnostics.single().span)
    }

    @Test fun readErrorsArePassedThrough() =
        assertEquals(
            listOf("IDL204"),
            codes(decodeFile(file("(parz 1 (schema shop 1) Status)", "(failed (why \"\\q\") (code 1))"), shop, id).diagnostics),
        )
}
