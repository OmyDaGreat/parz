package xyz.malefic.parz.tooling.cli

import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectiveTest {
    private val session = Session()

    @Test
    fun helpListsEveryDirective() {
        val out = session.directiveOutput("#help")
        directives.values.forEach { assertContains(out, it.usage) }
    }

    @Test
    fun list() {
        session.output("record A { x: bool }\ntype B = A")
        assertEquals("  A\n  B\n", session.directiveOutput("#list"))
    }

    @Test
    fun show() {
        session.output("record A { x: bool }")
        assertEquals("record A {\n  x: bool,\n}\n", session.directiveOutput("#show A"))
        assertEquals("No declaration named Z\n", session.directiveOutput("#show Z"))
        assertEquals("usage: #show <name>\n", session.directiveOutput("#show"))
        assertEquals("usage: #show <name>\n", session.directiveOutput("#show A B"))
    }

    @Test
    fun trailingTerminatorIgnored() {
        session.output("record A { x: bool }")
        assertEquals("record A {\n  x: bool,\n}\n", session.directiveOutput("#show A;;"))
    }

    @Test
    fun reset() {
        session.output("record A { x: bool }")
        session.directiveOutput("#reset")
        assertTrue(session.decls.isEmpty())
    }

    @Test
    fun debugToggles() {
        assertEquals("debug on\n", session.directiveOutput("#debug"))
        assertTrue(session.debug)
        assertEquals("debug off\n", session.directiveOutput("#debug"))
        assertFalse(session.debug)
    }

    @Test
    fun load() {
        val file = createTempFile(suffix = ".idl")
        try {
            file.writeText("record A { x: bool }")
            session.directiveOutput("#load $file")
            assertEquals(listOf("A"), session.decls.keys.toList())
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun loadMissingFile() = assertContains(session.directiveOutput("#load /no/such/file.idl"), "File not found: /no/such/file.idl")

    @Test
    fun loadUsage() = assertEquals("usage: #load <file>\n", session.directiveOutput("#load"))

    @Test
    fun quit() {
        session.directiveOutput("#quit")
        assertFalse(session.running)
    }

    @Test
    fun unknownDirective() = assertEquals("Unknown directive #nope. Try #help\n", session.directiveOutput("#nope"))
}
