package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant.Member
import xyz.malefic.parz.idl.SchemaErrorKind.CyclicType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cycle checking: aliases must not be recursive, and every record and variant must be buildable as a finite value.
 */
class Cycles {
    private fun cycles(vararg decls: Decl): List<String> {
        val diagnostics = validate(decls.toList()).diagnostics.map { it.kind }
        assertTrue(diagnostics.all { it is CyclicType }, "only cycle errors expected, got $diagnostics")
        return diagnostics.map { (it as CyclicType).path.joinToString(" -> ") }
    }

    private fun ref(name: String) = Type.Ref(name)

    private fun record(
        name: String,
        vararg fields: Pair<String, Type>,
    ) = Decl.Record(name, fields.map { Field(it.first, it.second) })

    private fun member(
        name: String,
        tag: Int,
        vararg fields: Pair<String, Type>,
    ) = Member(name, tag, fields.map { Field(it.first, it.second) })

    @Test
    fun variantWithBaseCase() =
        assertEquals(emptyList(), cycles(Decl.Variant("E", listOf(member("lit", 0), member("neg", 1, "x" to ref("E"))))))

    @Test
    fun variantWithoutBaseCase() = assertEquals(listOf("E -> E"), cycles(Decl.Variant("E", listOf(member("a", 0, "x" to ref("E"))))))

    @Test
    fun variantWhereEveryMemberRecurses() =
        assertEquals(listOf("E -> E"), cycles(Decl.Variant("E", listOf(member("a", 0, "x" to ref("E")), member("b", 1, "y" to ref("E"))))))

    @Test
    fun indirectThroughRecords() {
        val found =
            cycles(
                Decl.Variant("A", listOf(member("x", 0, "b" to ref("B")), member("y", 1, "c" to ref("C")))),
                record("B", "a" to ref("A")),
                record("C", "a" to ref("A")),
            )
        assertEquals(1, found.size, "$found")
        assertTrue("A" in found.single().split(" -> "))
    }

    @Test
    fun dependentsNotReported() {
        val found =
            cycles(
                Decl.Variant("A", listOf(member("x", 0, "b" to ref("B")))),
                record("B", "a" to ref("A")),
                record("D", "a" to ref("A")),
            )
        assertEquals(1, found.size, "$found")
        assertTrue("D" !in found.single().split(" -> "))
    }

    @Test
    fun recordCycle() = assertEquals(listOf("A -> B -> A"), cycles(record("A", "b" to ref("B")), record("B", "a" to ref("A"))))

    @Test
    fun recordSelfCycle() = assertEquals(listOf("A -> A"), cycles(record("A", "a" to ref("A"))))

    @Test
    fun guardedByOptionOrSeq() {
        assertEquals(emptyList(), cycles(record("R", "next" to Type.Option(ref("R")))))
        assertEquals(emptyList(), cycles(record("R", "kids" to Type.Seq(ref("R")))))
    }

    @Test
    fun mutualRecursionWithExit() =
        assertEquals(
            emptyList(),
            cycles(
                Decl.Variant("Expr", listOf(member("lit", 0), member("call", 1, "f" to ref("Fn")))),
                record("Fn", "body" to ref("Expr")),
            ),
        )

    @Test
    fun recordThroughAlias() = assertEquals(listOf("B -> A -> B"), cycles(Decl.Alias("A", ref("B")), record("B", "a" to ref("A"))))

    @Test
    fun aliasSelf() = assertEquals(listOf("A -> A"), cycles(Decl.Alias("A", ref("A"))))

    @Test
    fun aliasThroughOption() = assertEquals(listOf("A -> A"), cycles(Decl.Alias("A", Type.Option(ref("A")))))

    @Test
    fun aliasPairReportedOnce() = assertEquals(1, cycles(Decl.Alias("B", ref("C")), Decl.Alias("C", ref("B"))).size)

    @Test
    fun aliasLeadingIntoCycle() =
        assertEquals(
            listOf("B -> C -> B"),
            cycles(Decl.Alias("A", ref("B")), Decl.Alias("B", ref("C")), Decl.Alias("C", ref("B"))),
        )

    @Test
    fun aliasCycleDoesNotHideRecordCycle() =
        assertEquals(
            2,
            cycles(Decl.Alias("X", ref("X")), record("A", "b" to ref("B")), record("B", "a" to ref("A"))).size,
        )

    @Test
    fun cycleIsAttachedToFirstDeclInPath() {
        val kind = validate(listOf(record("A", "b" to ref("B")), record("B", "a" to ref("A")))).diagnostics.single().kind as CyclicType
        assertEquals(kind.path.first(), kind.decl.name)
        assertEquals("Cyclic type definition: A -> B -> A", kind.message)
    }

    @Test
    fun unknownRefsAreNotCycles() {
        val kinds = validate(listOf(record("A", "x" to ref("Nope")))).diagnostics.map { it.kind }
        assertTrue(kinds.none { it is CyclicType }, "$kinds")
    }
}
