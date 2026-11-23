package xyz.malefic.parz.tooling.cli

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionTest {
    private val session = Session()

    @Test
    fun acceptsAndPrints() {
        assertEquals("record A {\n  x: bool,\n}\n", session.output("record A { x: bool }"))
        assertEquals(listOf("A"), session.decls.keys.toList())
    }

    @Test
    fun emptyEntryPrintsNothing() = assertEquals("", session.output(""))

    @Test
    fun reportsSchemaErrorWithLocation() {
        val out = session.output("record A { x: Nope }")
        assertContains(out, "ERROR: Unknown type in field x: Nope at line 1:15")
        assertTrue(session.decls.isEmpty())
    }

    @Test
    fun reportsSyntaxErrorWithLocation() {
        val out = session.output("record A { x bool }")
        assertContains(out, "ERROR: Expected ':', but instead found 'bool' at line 1:14")
        assertTrue(session.decls.isEmpty())
    }

    @Test
    fun reportsShapeErrorWithLocation() {
        val out = session.output("record A {}")
        assertContains(out, "ERROR: Record must have at least one field at line 1:1")
        assertTrue(session.decls.isEmpty())
    }

    @Test
    fun keepsGoodDeclsBesideBadOnes() {
        val out = session.output("record A { x: Nope }\nrecord B { y: bool }")
        assertEquals(listOf("B"), session.decls.keys.toList())
        assertContains(out, "record B {")
    }

    @Test
    fun laterEntriesSeeEarlierDecls() {
        session.output("record A { x: bool }")
        val out = session.output("record B { a: A }")
        assertFalse("ERROR" in out, out)
        assertEquals(listOf("A", "B"), session.decls.keys.toList())
    }

    @Test
    fun rejectsRedefinition() {
        session.output("record A { x: bool }")
        val out = session.output("record A { y: bool }")
        assertContains(out, "Duplicate declaration: A")
        assertContains(
            session.decls
                .getValue("A")
                .first
                .pretty(),
            "x: bool",
        )
    }

    @Test
    fun errorsSortedBySourcePosition() {
        val out = session.output("record B { y: Nope2 }\nrecord A { x: Nope1 }")
        assertTrue(out.indexOf("Nope2") < out.indexOf("Nope1"), out)
    }

    @Test
    fun debugPrintsSyntaxTree() {
        session.debug = true
        assertContains(session.output("record A { x: bool }"), "Record(name=A")
    }

    @Test
    fun cycleReportedWithoutCascade() {
        val out = session.output("record A { b: B }\nrecord B { a: A }")
        assertContains(out, "Cyclic type definition: A -> B -> A")
        assertFalse("Unknown type" in out, out)
        assertTrue(session.decls.isEmpty())
    }

    @Test
    fun warningsAreShownButDoNotReject() {
        session.output("record P { x: bool }")
        val out = session.output("record A { e: id<P> }")
        assertContains(out, "WARNING: No table<P> for id<P> in field e at line 1:15")
        assertContains(out, "record A {\n  e: id<P>,\n}")
        assertEquals(listOf("P", "A"), session.decls.keys.toList())
    }

    @Test
    fun warningGoesAwayOnceTheTableExists() {
        session.output("record P { x: bool }")
        session.output("record A { e: id<P> }")
        val out = session.output("record Prog { ps: table<P> }")
        assertFalse(out.contains("WARNING"), out)
        assertEquals(listOf("P", "A", "Prog"), session.decls.keys.toList())
    }

    @Test
    fun warningsAreReportedOnce() {
        session.output("record P { x: bool }")
        val out = session.output("record A { e: id<P> } record B { x: bool }")
        assertEquals(1, Regex("WARNING").findAll(out).count(), out)
    }

    @Test
    fun rejectedDeclarationsOnlyShowErrors() {
        session.output("record P { x: bool }")
        val out = session.output("record A { e: id<P>, y: Nope }")
        assertContains(out, "ERROR: Unknown type in field y: Nope")
        assertFalse(out.contains("WARNING"), out)
        assertEquals(listOf("P"), session.decls.keys.toList())
    }
}
