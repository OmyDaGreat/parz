package xyz.malefic.parz.idl.encoding

data class SchemaId(
    val name: String,
    val version: Int,
) {
    init {
        require(name.isNotEmpty() && name.none { it.isDelimiter() }) { "Invalid schema name: '$name'" }
        require(version >= 0) { "Invalid schema version: '$version'" }
    }

    fun toSexp() = Sexp.List(listOf(Sexp.Atom("schema"), Sexp.Atom(name), Sexp.Atom("$version")))

    override fun toString() = "$name $version"
}
