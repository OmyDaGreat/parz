package xyz.malefic.parz.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Collecting {
    @Test
    fun keepsValueAndDiagnosticsInOrder() {
        val result =
            collecting<Int, String> {
                contextOf<Sink<String>>().report("first")
                contextOf<Sink<String>>().report("second")
                42
            }
        assertEquals(42, result.value)
        assertEquals(listOf("first", "second"), result.diagnostics)
    }

    @Test
    fun okOnlyWithoutDiagnostics() {
        assertTrue(collecting<Unit, String> { }.ok)
        assertFalse(collecting<Unit, String> { contextOf<Sink<String>>().report("problem") }.ok)
    }

    @Test
    fun errorsIsCheckedUnit() {
        val errors: Errors<String> = collecting { contextOf<Sink<String>>().report("problem") }
        assertEquals(Unit, errors.value)
        assertEquals(listOf("problem"), errors.diagnostics)
    }

    @Test
    fun sinkReturnsTheContextSink() {
        val result = collecting { sink().report(Diagnostic(TestKind(), null)) }
        assertEquals(listOf(Diagnostic(TestKind(), null)), result.diagnostics)
    }

    @Test
    fun severityDefaultsToError() = assertEquals(Severity.ERROR, TestKind().severity)
}
