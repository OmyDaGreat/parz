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

    init {
        val problems = validate(decls)
        require(problems.isEmpty()) { "Schema validation failed: ${problems.joinToString { it.message }}" }
    }

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

data class SchemaProblem(
    val decl: String,
    val field: String?,
    val message: String,
)

context(schemaProblems: MutableList<SchemaProblem>)
fun List<Decl>.distinctSchema(): List<Decl> {
    val set = HashSet<String>()
    val list = ArrayList<Decl>()
    for (e in this) {
        val key = e.name.generatorDistinct()
        if (set.add(key)) list.add(e) else schemaProblems.add(SchemaProblem(e.name, null, "Duplicate declaration name"))
    }
    return list
}

fun MutableList<SchemaProblem>.add(
    decl: String,
    field: String?,
    message: String,
) {
    this.add(SchemaProblem(decl, field, message))
}

fun validate(decls: List<Decl>): List<SchemaProblem> =
    buildList {
        val distinct = decls.distinctSchema()
        distinct.forEach { decl ->
            when (decl) {
                is Decl.Record -> {
                    val fieldNames = mutableSetOf<String>()
                    decl.type.fields.forEach { field ->
                        if (!fieldNames.add(field.name.generatorDistinct())) {
                            add(decl.name, field.name, "Duplicate field name")
                        }
                    }
                }

                is Decl.Enum -> {
                    val memberNames = mutableSetOf<String>()
                    val memberTags = mutableSetOf<Int>()
                    decl.type.members.forEach { member ->
                        if (!memberNames.add(member.name.generatorDistinct())) {
                            add(decl.name, member.name, "Duplicate member name")
                        }
                        if (!memberTags.add(member.tag)) {
                            add(decl.name, member.name, "Duplicate member tag")
                        }
                    }
                }

                is Decl.Alias -> {
                    // No additional validation needed for basic transparent aliases
                }
            }
        }
    }
