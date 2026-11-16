package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan

context(sink: Sink<Diagnostic<SexpErrorKind>>)
fun readSexps(source: String): List<Sexp> = Reader(source, sink).all()

private class Reader(
    private val source: String,
    private val sink: Sink<Diagnostic<SexpErrorKind>>,
) {
    private var i = 0

    fun all(): List<Sexp> = buildList { while (skip()) readOne()?.let(::add) }

    private fun skip(): Boolean {
        while (i < source.length) {
            when {
                source[i].isWhitespace() -> i++
                source[i] == ';' -> while (i < source.length && source[i] != '\n') i++
                else -> return true
            }
        }
        return false
    }

    private fun readOne(): Sexp? =
        when (source[i]) {
            '(' -> list()
            ')' -> null.also { report(SexpErrorKind.UnexpectedClose, i, i++) }
            '"' -> str()
            else -> atom()
        }

    private fun list(): Sexp.List {
        val start = i++
        val items = mutableListOf<Sexp>()
        while (true) {
            if (!skip()) {
                report(SexpErrorKind.UnclosedList, start, start + 1)
                return Sexp.List(items, SourceSpan(start, i))
            }
            if (source[i] == ')') return Sexp.List(items, SourceSpan(start, i++))
            readOne()?.let(items::add)
        }
    }

    private fun Char.isDelimiter() = isWhitespace() || this in "()\";"

    private fun atom(): Sexp.Atom {
        val start = i
        while (i < source.length && !source[i].isDelimiter()) i++
        return Sexp.Atom(source.substring(start, i), SourceSpan(start, i))
    }

    private fun str(): Sexp.Str {
        val start = i++
        val value =
            buildString {
                while (i < source.length && source[i] != '"') {
                    val c = source[i++]
                    if (c != '\\') {
                        append(c)
                        continue
                    }
                    val escapeStart = i - 1
                    if (i >= source.length) break
                    when (source[i++]) {
                        '"' -> append('"')
                        '\\' -> append('\\')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        'u' -> unicode(escapeStart)?.let(::append)
                        else -> report(SexpErrorKind.BadEscape, escapeStart, i)
                    }
                }
            }
        if (i >= source.length) {
            report(SexpErrorKind.UnterminatedString, start, start + 1)
            return Sexp.Str(value, SourceSpan(start, i))
        }
        i++
        return Sexp.Str(value, SourceSpan(start, i))
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun unicode(escapeStart: Int): String? {
        fun reportNull(
            kind: SexpErrorKind,
            start: Int,
            end: Int,
        ) = report(kind, start, end).let { null }

        if (source.getOrNull(i) != '{') return reportNull(SexpErrorKind.BadEscape, escapeStart, i)
        val digitsStart = ++i
        while (i < source.length && source[i].isHex()) i++
        val digits = source.substring(digitsStart, i)
        if (source.getOrNull(i) != '}' || digits.isEmpty() || digits.length > 6) return reportNull(SexpErrorKind.BadEscape, escapeStart, i)
        i++
        val code = digits.toInt(16)
        if (code > 0x10FFFF || code in 0xD800..0xDFFF) return reportNull(SexpErrorKind.BadEscape, escapeStart, i)
        if (code < 0x10000) return code.toChar().toString()
        val v = code - 0x10000
        return charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }

    private fun report(
        kind: SexpErrorKind,
        start: Int,
        end: Int,
    ) = sink.report(Diagnostic(kind, SourceSpan(start, end)))
}
