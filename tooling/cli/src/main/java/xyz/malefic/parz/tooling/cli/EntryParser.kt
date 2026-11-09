package xyz.malefic.parz.tooling.cli

import org.jline.reader.EOFError
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.ParsedLine
import org.jline.reader.Parser
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.TerminalBuilder
import kotlin.io.path.Path

private class EntryParser : Parser {
    private val words = DefaultParser()

    override fun parse(
        line: String,
        cursor: Int,
        context: Parser.ParseContext,
    ): ParsedLine {
        val done = line.isBlank() || line.trimEnd().endsWith(";;") || line.trimStart().startsWith("#")
        if (context == Parser.ParseContext.ACCEPT_LINE && !done) throw EOFError(-1, -1, "entry not finished", ";;")
        return words.parse(line, cursor, context)
    }
}

fun entries(): Sequence<String> = if (System.console()?.isTerminal == true) interactive() else piped()

private fun interactive() =
    sequence {
        val terminal = TerminalBuilder.builder().system(true).build()
        val reader =
            LineReaderBuilder
                .builder()
                .terminal(
                    terminal,
                ).parser(EntryParser())
                .variable(
                    LineReader.SECONDARY_PROMPT_PATTERN,
                    "  ..> ",
                ).variable(LineReader.HISTORY_FILE, Path(System.getProperty("user.home"), ".parz_history"))
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build()
        terminal.use {
            while (true) {
                val entry =
                    try {
                        reader.readLine("parz> ")
                    } catch (_: UserInterruptException) {
                        continue
                    } catch (_: EndOfFileException) {
                        break
                    }
                if (entry.isNotBlank()) yield(entry.trimEnd().removeSuffix(";;"))
            }
        }
    }

private fun piped() =
    sequence {
        val buffer = StringBuilder()
        while (true) {
            val line = readlnOrNull() ?: break
            val trimmed = line.trimEnd()
            if (buffer.isBlank() && trimmed.trimStart().startsWith("#")) {
                yield(trimmed)
                continue
            }
            if (trimmed.endsWith(";;")) {
                buffer.appendLine(trimmed.removeSuffix(";;"))
                yield(buffer.toString())
                buffer.clear()
            } else {
                buffer.appendLine(trimmed)
            }
        }
        if (buffer.isNotBlank()) yield(buffer.toString())
    }
