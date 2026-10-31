package xyz.malefic.parz.idl.syntax

class Parser(
    private val tokens: List<Token>,
) {
    private var pos = 0

    val done get() = pos >= tokens.size

    fun accept(c: Token.Symbol): Boolean {
        if (done) return false
        if (tokens[pos] is Token.Symbol && (tokens[pos] as Token.Symbol).char == c.char) {
            pos++
            return true
        }
        return false
    }

    fun expect(c: Token.Symbol) {
        if (!accept(c)) throw SyntaxError("Expected '${c.char}'", tokens.getOrNull(pos)?.offset ?: -1)
    }
}
