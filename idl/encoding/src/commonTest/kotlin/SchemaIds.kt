package xyz.malefic.parz.idl.encoding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaIds {
    @Test fun printsAsTheHeaderPart() = assertEquals(list(atom("schema"), atom("ir"), atom("3")), SchemaId("ir", 3).toSexp())

    @Test fun readableToString() = assertEquals("ir 3", SchemaId("ir", 3).toString())

    @Test fun versionZeroIsAllowed() = assertEquals("ir 0", SchemaId("ir", 0).toString())

    @Test fun invalidNames() {
        listOf("", "my ir", "ir(2)", "\"ir\"", "a;b", "tab\tname").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { SchemaId(name, 1) }
        }
    }

    @Test fun negativeVersion() {
        assertFailsWith<IllegalArgumentException> { SchemaId("ir", -1) }
    }
}
