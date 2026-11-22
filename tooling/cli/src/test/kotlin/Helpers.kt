package xyz.malefic.parz.tooling.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Run [block] and return everything it printed to standard output.
 */
fun captured(block: () -> Unit): String {
    val original = System.out
    val buffer = ByteArrayOutputStream()
    System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
    try {
        block()
    } finally {
        System.setOut(original)
    }
    return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n")
}

fun Session.output(source: String) = captured { run(source) }

fun Session.directiveOutput(line: String) = captured { directive(line) }
