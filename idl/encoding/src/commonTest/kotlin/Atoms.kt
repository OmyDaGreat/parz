package xyz.malefic.parz.idl.encoding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [Sexp.Atom] only holds text that reads back as the same single atom.
 */
class Atoms {
    @Test
    fun rejectsTextThatWouldNotReadBack() {
        listOf("", "a b", "(", "a)", "\"", "a;b", "\t", "a\nb").forEach { text ->
            assertFailsWith<IllegalArgumentException>("'$text' should be rejected") { Sexp.Atom(text) }
        }
    }

    @Test
    fun acceptsOtherText() {
        listOf("a", "-0.05", "a.b", "TOWARDS_ZERO", "x/y", "é").forEach { assertEquals(it, Sexp.Atom(it).text) }
    }
}
