package xyz.malefic.parz.idl.encoding

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
}
