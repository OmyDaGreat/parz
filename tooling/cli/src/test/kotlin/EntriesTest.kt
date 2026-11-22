package xyz.malefic.parz.tooling.cli

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Splitting piped standard input into entries ended by `;;`, with `#` directives on their own lines.
 */
class EntriesTest {
    private fun entriesFrom(input: String): List<String> {
        val original = System.`in`
        System.setIn(input.byteInputStream())
        try {
            return entries().toList()
        } finally {
            System.setIn(original)
        }
    }

    @Test
    fun singleEntry() = assertEquals(listOf("record A { x: bool }\n"), entriesFrom("record A { x: bool };;\n"))

    @Test
    fun multiLineEntry() = assertEquals(listOf("record A {\n  x: bool\n}\n"), entriesFrom("record A {\n  x: bool\n};;\n"))

    @Test
    fun severalEntries() = assertEquals(listOf("type A = bool\n", "type B = bool\n"), entriesFrom("type A = bool;;\ntype B = bool;;\n"))

    @Test
    fun directiveOnItsOwnLine() =
        assertEquals(listOf("type A = bool\n", "#list", "#show A"), entriesFrom("type A = bool;;\n#list\n#show A\n"))

    @Test
    fun hashInsideEntryIsNotADirective() = assertEquals(listOf("record A {\n#list\n}\n"), entriesFrom("record A {\n#list\n};;\n"))

    @Test
    fun unterminatedLastEntry() = assertEquals(listOf("type A = bool\n"), entriesFrom("type A = bool"))

    @Test
    fun emptyInput() = assertEquals(emptyList(), entriesFrom(""))
}
