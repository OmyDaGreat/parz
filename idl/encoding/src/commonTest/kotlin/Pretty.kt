package xyz.malefic.parz.idl.encoding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [pretty]: one line if it fits, otherwise the head stays after `(` and the rest go one per line, indented 2.
 */
class Pretty {
    private val numbers = one("(-12.34 -12.34 -12.34 -12.34 1)")

    private val program = one("(program (name \"demo\") (body (move 1 x) (move 2 y)))")

    private fun assertWithinWidth(
        printed: String,
        width: Int,
    ) = printed.lines().forEach { assertTrue(it.length <= width, "line longer than $width:\n$it\nin:\n$printed") }

    @Test fun atomsAndStringsAreUnchanged() {
        assertEquals("pending", atom("pending").pretty(1))
        assertEquals("\"a long string\"", str("a long string").pretty(1))
    }

    @Test fun emptyListNeverBreaks() = assertEquals("()", list().pretty(1))

    @Test fun fitsOnOneLine() {
        assertEquals(numbers.print(), numbers.pretty())
        assertEquals(program.print(), program.pretty())
    }

    @Test fun exactlyTheWidthStaysFlat() = assertEquals(numbers.print(), numbers.pretty(31))

    @Test fun oneOverTheWidthBreaks() = assertEquals("(-12.34\n  -12.34\n  -12.34\n  -12.34\n  1)", numbers.pretty(30))

    @Test fun innerListsStayFlatWhenTheyFit() =
        assertEquals(
            """
            (program
              (name "demo")
              (body
                (move 1 x)
                (move 2 y)))
            """.trimIndent(),
            program.pretty(30),
        )

    @Test fun closingParensCountTowardsTheWidth() {
        val tree = one("(x (y zzzzzzzz))")
        val printed = tree.pretty(14)
        assertEquals("(x\n  (y\n    zzzzzzzz))", printed)
        assertWithinWidth(printed, 14)
    }

    @Test fun headListBreaks() = assertEquals("((a\n   b\n   c\n   d)\n  x)", one("((a b c d) x)").pretty(8))

    @Test fun stringsAreMeasuredWithQuotes() {
        val tree = one("(k \"abcdefgh\")") // 14 characters flat
        assertEquals("(k \"abcdefgh\")", tree.pretty(14))
        assertEquals("(k\n  \"abcdefgh\")", tree.pretty(13))
    }

    @Test fun stringsAreMeasuredAsEscaped() {
        val tree = list(atom("k"), str("a\nb")) // (k "a\nb") is 10 characters flat
        assertEquals("(k \"a\\nb\")", tree.pretty(10))
        assertEquals("(k\n  \"a\\nb\")", tree.pretty(9))
    }

    @Test fun longAtomsOverflowInsteadOfBreaking() =
        assertEquals(
            "(a\n  aaaaaaaaaaaaaaaaaaaa)",
            list(atom("a"), atom("a".repeat(20))).pretty(10),
        )

    @Test fun singleItemListThatDoesNotFit() = assertEquals("(abcdefghij)", one("(abcdefghij)").pretty(5))

    @Test fun staysWithinWidthWhenPossible() {
        val tree = one("(program (name \"demo\") (data (item x (ty 9 2)) (item y (ty 5 0))) (body (move 1 x) (add x y (ty 9 2)) ()))")
        (16..60).forEach { assertWithinWidth(tree.pretty(it), it) }
    }

    @Test fun readsBackAsTheSameTree() {
        val trees =
            listOf(
                numbers,
                program,
                one("((a b c d) x)"),
                list(atom("k"), str("a\nb \"quoted\" 😀"), list()),
                one("(a (b (c (d (e (f (g h)))))))"),
            )
        trees.forEach { tree ->
            (1..40).forEach { width ->
                val printed = tree.pretty(width)
                assertEquals(tree.withoutSpans(), one(printed).withoutSpans(), "width $width:\n$printed")
            }
        }
    }

    @Test fun deterministic() = assertEquals(program.pretty(20), program.pretty(20))
}
