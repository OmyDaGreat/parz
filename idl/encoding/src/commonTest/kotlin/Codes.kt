package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.idl.Reason
import kotlin.test.Test
import kotlin.test.assertEquals

class Codes {
    private val kinds =
        listOf(
            SexpErrorKind.UnexpectedClose,
            SexpErrorKind.UnclosedList,
            SexpErrorKind.UnterminatedString,
            SexpErrorKind.BadEscape,
        )

    @Test
    fun codes() = assertEquals(listOf("IDL201", "IDL202", "IDL203", "IDL204"), kinds.map { it.code })

    @Test
    fun messages() =
        assertEquals(
            listOf("Unexpected ')'", "Unclosed '('", "Unterminated string", "Invalid escape sequence"),
            kinds.map { it.message },
        )

    private val codecKinds =
        listOf(
            CodecErrorKind.Expected("a decimal"),
            CodecErrorKind.UnknownMember("m"),
            CodecErrorKind.MissingField("f"),
            CodecErrorKind.UnknownField("f"),
            CodecErrorKind.DuplicateField("f"),
            CodecErrorKind.DoesNotFit(Reason.Fit.TooLong(1, 2)),
            CodecErrorKind.ExpectedOneValue(0),
            CodecErrorKind.MissingHeader,
            CodecErrorKind.UnsupportedVersion("2"),
            CodecErrorKind.SchemaMismatch(SchemaId("ir", 3), "(schema ir 2)"),
            CodecErrorKind.UnknownRootType("Nope"),
            CodecErrorKind.WrongRootType("Program", "Line"),
        )

    @Test
    fun codecCodes() =
        assertEquals(
            listOf(
                "IDL210",
                "IDL211",
                "IDL212",
                "IDL213",
                "IDL214",
                "IDL215",
                "IDL216",
                "IDL217",
                "IDL218",
                "IDL219",
                "IDL220",
                "IDL221",
            ),
            codecKinds.map {
                it.code
            },
        )

    @Test
    fun codecMessages() =
        assertEquals(
            listOf(
                "Expected a decimal",
                "Unknown member 'm'",
                "Missing field 'f'",
                "Unknown field 'f'",
                "Duplicate field 'f'",
                "Too long (2 > 1)",
                "Expected exactly one value, found 0",
                "Missing header",
                "Unsupported format version '2'",
                "Schema mismatch: expected 'ir 3', found '(schema ir 2)'",
                "Unknown root type 'Nope'",
                "Wrong root type: expected 'Program', found 'Line'",
            ),
            codecKinds.map { it.message },
        )
}
