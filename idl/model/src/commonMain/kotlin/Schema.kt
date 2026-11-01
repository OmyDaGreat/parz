package xyz.malefic.parz.idl

sealed interface Decl {
    val name: String

    data class Record(
        override val name: String,
        val type: Type.Record,
    ) : Decl

    data class Enum(
        override val name: String,
        val type: Type.Enum,
    ) : Decl

    data class Alias(
        override val name: String,
        val type: Type,
    ) : Decl
}

data class Schema(
    val decls: List<Decl>,
) {
    private val byName = decls.associateBy { it.name }

    operator fun get(name: String) = byName[name]

    fun resolve(type: Type): Type =
        when (type) {
            is Type.Ref -> {
                when (val decl = byName.getValue(type.name)) {
                    is Decl.Record -> decl.type
                    is Decl.Enum -> decl.type
                    is Decl.Alias -> resolve(decl.type)
                }
            }

            else -> {
                type
            }
        }

    companion object {
        val EMPTY = Schema(emptyList())
    }
}
