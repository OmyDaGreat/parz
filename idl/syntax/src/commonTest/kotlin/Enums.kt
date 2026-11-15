package xyz.malefic.parz.idl.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Enums {
    @Test
    fun enumIsVariantMarkedAsEnum() {
        val source = "enum Status { pending, settled = 5, closed }"
        val status = decl<DeclSyntax.Variant>(source)
        assertTrue(status.isEnum)
        assertEquals(source.span("enum Status"), status.span)
        assertEquals(listOf("pending", "settled", "closed"), status.members.map { it.name })
        assertEquals(listOf(0, 5, 6), status.members.map { it.tag })
    }

    @Test
    fun enumWithFieldsStillParses() {
        // Rejected when lowered to the model (see Lowering), not by the parser.
        val status = decl<DeclSyntax.Variant>("enum Status { pending(since: string) }")
        assertTrue(status.isEnum)
        assertEquals(
            listOf("since"),
            status.members
                .single()
                .payload
                .map { it.name },
        )
    }
}
