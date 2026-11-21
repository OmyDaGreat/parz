package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.idl.Reason
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Collection.Option
import xyz.malefic.parz.idl.Type.Collection.Seq
import xyz.malefic.parz.idl.Type.Primitive.Bool
import xyz.malefic.parz.idl.Type.Primitive.Str
import xyz.malefic.parz.idl.Type.Primitive.Void
import xyz.malefic.parz.idl.encoding.CodecErrorKind.DoesNotFit
import xyz.malefic.parz.idl.encoding.CodecErrorKind.DuplicateField
import xyz.malefic.parz.idl.encoding.CodecErrorKind.ExpectedOneValue
import xyz.malefic.parz.idl.encoding.CodecErrorKind.MissingField
import xyz.malefic.parz.idl.encoding.CodecErrorKind.UnknownField
import xyz.malefic.parz.idl.encoding.CodecErrorKind.UnknownMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * What [decodeString] reports for input that doesn't match the type, and where.
 */
class DecodeErrors {
    /**
     * The single error decoding [text], which must be underlined at [at] (the [nth] occurrence).
     */
    private fun error(
        text: String,
        type: Type,
        at: String = text,
        nth: Int = 0,
    ): AnyDiagnostic {
        val diagnostics = decodeErrors(text, type)
        assertEquals(1, diagnostics.size, "expected one error decoding $text, got $diagnostics")
        assertEquals(text.span(at, nth), diagnostics.single().span, "span decoding $text")
        return diagnostics.single()
    }

    private fun expected(
        text: String,
        type: Type,
        at: String = text,
    ) {
        assertIs<CodecErrorKind.Expected>(error(text, type, at).kind, text)
    }

    @Test fun wrongFormForEachType() {
        listOf(
            "x" to Void,
            "(())" to Void,
            "\"true\"" to Bool,
            "TRUE" to Bool,
            "()" to Bool,
            "\"1\"" to money,
            "(1)" to money,
            "hello" to Str(),
            "()" to Str(),
            "null" to Option(Bool),
            "(some)" to Option(Bool),
            "(some true false)" to Option(Bool),
            "(none)" to Option(Bool),
            "1" to Seq(money),
            "\"a\"" to line,
            "amount" to line,
            "\"pending\"" to status,
            "()" to status,
            "((failed))" to status,
        ).forEach { (text, type) -> expected(text, type) }
    }

    @Test fun malformedDecimals() {
        listOf("1e5", "+1", ".5", "1.", "1..2", "--1", "1,000", "0x10", "١٢", "１２", "NaN").forEach { expected(it, money) }
    }

    @Test fun messages() {
        assertEquals("Expected true or false", error("yes", Bool).kind.message)
        assertEquals("Expected a decimal", error("1e5", money).kind.message)
        assertEquals("Expected a string", error("x", Str()).kind.message)
    }

    @Test fun outOfRange() {
        val kind = assertIs<DoesNotFit>(error("1000.01", money).kind)
        assertIs<Reason.OutOfRange>(kind.reason)
        assertIs<DoesNotFit>(error("-1", count).kind)
        assertIs<DoesNotFit>(error("256", count).kind)
    }

    @Test fun tooManyDecimalPlaces() {
        val diagnostic = error("1.505", money)
        assertEquals(DoesNotFit(Reason.InexactScale(2, 3)), diagnostic.kind)
        assertEquals("Too many decimal places (at most 2)", diagnostic.kind.message)
        assertIs<DoesNotFit>(error("125", decimal(-100, 100, -1)).kind)
        assertIs<DoesNotFit>(error("0.5", count).kind)
    }

    @Test fun stringTooLong() {
        val diagnostic = error("\"123456789\"", Str(8))
        assertEquals(DoesNotFit(Reason.TooLong(8, 9)), diagnostic.kind)
        assertEquals("Too long (9 > 8)", diagnostic.kind.message)
    }

    @Test fun errorPointsAtTheLeafNotTheRecord() {
        assertIs<DoesNotFit>(error("((amount 1.505) (note none))", line, at = "1.505").kind)
        assertIs<CodecErrorKind.Expected>(error("((amount 1) (note (some 5)))", line, at = "5").kind)
    }

    @Test fun unknownMemberUnderlinesTheName() {
        assertEquals(UnknownMember("cancelled"), error("cancelled", status).kind)
        assertEquals(UnknownMember("cancelled"), error("(cancelled (why \"x\"))", status, at = "cancelled").kind)
    }

    @Test fun memberWithoutFieldsWrittenAsAList() = expected("(pending)", status)

    @Test fun memberWithFieldsWrittenBare() = expected("failed", status)

    @Test fun missingFieldUnderlinesTheRecord() {
        assertEquals(MissingField("note"), error("((amount 1))", line).kind)
        assertEquals(MissingField("code"), error("(failed (why \"x\"))", status).kind)
    }

    @Test fun everyMissingFieldIsReported() =
        assertEquals(
            listOf(MissingField("amount"), MissingField("note")),
            decodeErrors("()", line).map { it.kind },
        )

    @Test fun memberWithFieldsButNoneGiven() =
        assertEquals(
            listOf(MissingField("why"), MissingField("code")),
            decodeErrors("(failed)", status).map { it.kind },
        )

    @Test fun unknownFieldUnderlinesTheName() {
        val text = "((amount 1) (note none) (extra 2))"
        assertEquals(UnknownField("extra"), error(text, line, at = "extra").kind)
    }

    @Test fun duplicateFieldUnderlinesTheSecond() {
        val text = "((amount 1) (note none) (amount 2))"
        assertEquals(DuplicateField("amount"), error(text, line, at = "amount", nth = 1).kind)
    }

    @Test fun malformedFieldEntries() {
        listOf("(amount)", "(amount 1 2)", "(\"amount\" 1)", "amount", "((amount) 1)").forEach { entry ->
            val text = "($entry (note none))"
            val diagnostics = decodeErrors(text, line)
            assertEquals(listOf("IDL210", "IDL212"), codes(diagnostics), text)
            assertEquals(text.span(entry), diagnostics.first().span, text)
        }
    }

    @Test fun everyFieldErrorIsReportedInOnePass() =
        assertEquals(
            listOf("IDL215", "IDL213", "IDL214", "IDL212"),
            codes(decodeErrors("((amount 1.505) (bogus 1) (amount 2))", line)),
        )

    @Test fun everySeqItemErrorIsReported() {
        val text = "(1 x 2 1.505 y)"
        val diagnostics = decodeErrors(text, Seq(money))
        assertEquals(listOf("IDL210", "IDL215", "IDL210"), codes(diagnostics))
        assertEquals(listOf(text.span("x"), text.span("1.505"), text.span("y")), diagnostics.map { it.span })
    }

    @Test fun errorsDeepInsideNestedValues() {
        val text = "((lines (((amount 1) (note none)) ((amount x) (note none)))) (status (failed (why \"too long!\") (code 3))))"
        val diagnostics = decodeErrors(text, order)
        assertEquals(listOf("IDL210", "IDL215"), codes(diagnostics))
        assertEquals(listOf(text.span("x"), text.span("\"too long!\"")), diagnostics.map { it.span })
    }

    @Test fun emptyInput() {
        val diagnostics = decodeErrors("  ; nothing here", money)
        assertEquals(listOf(ExpectedOneValue(0)), diagnostics.map { it.kind })
    }

    @Test fun moreThanOneValue() = assertEquals(listOf(ExpectedOneValue(2)), decodeErrors("1 2", money).map { it.kind })

    @Test fun readErrorsArePassedThrough() {
        assertEquals(listOf("IDL204"), codes(decodeString("\"a\\qb\"", Str()).diagnostics))
        assertEquals(listOf("IDL201", "IDL216"), codes(decodeString(")", money).diagnostics))
    }

    @Test fun truncatedInputIsAnError() {
        val (value, diagnostics) = decodeString("((amount 1) (note none)", line, shop)
        assertEquals(listOf("IDL202"), codes(diagnostics))
        assertNull(value)
    }
}
