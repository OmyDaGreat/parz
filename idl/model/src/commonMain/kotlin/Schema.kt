package xyz.malefic.parz.idl

sealed interface Decl {
    val name: String

    data class Record(
        override val name: String,
        val fields: List<Field>,
    ) : Decl {
        init {
            require(fields.isNotEmpty()) { "Record must have at least one field" }
        }

        data class Field(
            val name: String,
            val type: Type,
        ) {
            init {
                require(name.isNotEmpty()) { "Record field name must not be empty" }
            }
        }
    }

    data class Enum(
        override val name: String,
        val members: List<Member>,
    ) : Decl {
        init {
            require(members.isNotEmpty()) { "Enum must have at least one member" }
        }

        data class Member(
            val name: String,
            val tag: Int,
        ) {
            init {
                require(name.isNotEmpty()) { "Enum member name must not be empty" }
                require(tag >= 0) { "Enum member tag must be non-negative" }
            }
        }
    }

    data class Alias(
        override val name: String,
        val type: Type,
    ) : Decl
}

sealed interface Resolved {
    data class Record(
        val decl: Decl.Record,
    ) : Resolved

    data class Enum(
        val decl: Decl.Enum,
    ) : Resolved

    data class Other(
        val type: Type,
    ) : Resolved
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

    fun resolve(type: Type): Resolved =
        when (type) {
            is Type.Ref -> {
                when (val decl = get(type.name) ?: error("Unresolved type reference in schema: ${type.name}")) {
                    is Decl.Record -> Resolved.Record(decl)
                    is Decl.Enum -> Resolved.Enum(decl)
                    is Decl.Alias -> resolve(decl.type)
                }
            }

            else -> {
                Resolved.Other(type)
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

fun Type.refs(): List<String> =
    when (this) {
        is Type.Ref -> listOf(name)
        is Type.Option -> inner.refs()
        is Type.Seq -> inner.refs()
        else -> emptyList()
    }

fun Decl.refs(): List<String> =
    when (this) {
        is Decl.Record -> fields.flatMap { it.type.refs() }
        is Decl.Enum -> emptyList()
        is Decl.Alias -> type.refs()
    }

fun Type.unguardedRefs(): List<String> =
    when (this) {
        is Type.Ref -> listOf(name)
        else -> emptyList()
    }

fun Decl.Record.unguardedRefs(): List<String> = fields.flatMap { it.type.unguardedRefs() }

private fun Decl.Alias.isCyclic(byName: Map<String, Decl>): Boolean {
    val seen = mutableSetOf<String>()
    val pending = ArrayDeque(type.refs())
    while (pending.isNotEmpty()) {
        val name = pending.removeLast()
        if (name == this.name) return true
        if (!seen.add(name)) continue
        (byName[name] as? Decl.Alias)?.let { pending += it.type.refs() }
    }
    return false
}

private fun recordCycles(edges: Map<String, List<String>>): List<List<String>> {
    val state = mutableMapOf<String, Boolean>()
    val path = ArrayDeque<String>()
    val cycles = mutableListOf<List<String>>()

    fun visit(name: String) {
        when (state[name]) {
            true -> {
                return
            }

            false -> {
                cycles += path.dropWhile { it != name } + name
                return
            }

            null -> {}
        }
        state[name] = false
        path.addLast(name)
        edges[name].orEmpty().forEach(::visit)
        path.removeLast()
        state[name] = true
    }

    edges.keys.forEach(::visit)
    return cycles
}

fun edgeTargets(
    name: String,
    byName: Map<String, Decl>,
): List<String> =
    when (val decl = byName[name]) {
        is Decl.Record -> listOf(name)
        is Decl.Alias -> decl.type.unguardedRefs().flatMap { edgeTargets(it, byName) }
        is Decl.Enum, null -> emptyList()
    }

fun validate(decls: List<Decl>): List<SchemaProblem> =
    buildList {
        val distinct = decls.distinctSchema()
        val declarations = distinct.mapTo(HashSet()) { it.name }
        var checkCycles = true
        distinct.forEach { decl ->
            when (decl) {
                is Decl.Record -> {
                    decl.fields.forEach { field ->
                        field.type
                            .refs()
                            .filter { it !in declarations }
                            .forEach { add(decl.name, field.name, "Unknown type: $it") }
                    }
                    decl.unguardedRefs().forEach { name ->
                        if (name == decl.name) {
                            add(decl.name, null, "Cyclic type definition: $name")
                            checkCycles = false
                        }
                    }
                    val fieldNames = mutableSetOf<String>()
                    decl.fields.forEach { field ->
                        if (!fieldNames.add(field.name.generatorDistinct())) {
                            add(decl.name, field.name, "Duplicate field name")
                        }
                    }
                }

                is Decl.Enum -> {
                    val memberNames = mutableSetOf<String>()
                    val memberTags = mutableSetOf<Int>()
                    decl.members.forEach { member ->
                        if (!memberNames.add(member.name.generatorDistinct())) {
                            add(decl.name, member.name, "Duplicate member name")
                        }
                        if (!memberTags.add(member.tag)) {
                            add(decl.name, member.name, "Duplicate member tag")
                        }
                    }
                }

                is Decl.Alias -> {
                    if (decl.isCyclic(byName = distinct.associateBy { it.name })) {
                        add(decl.name, null, "Cyclic type definition")
                        checkCycles = false
                    }
                    decl.type
                        .refs()
                        .filter { it !in declarations }
                        .forEach { add(decl.name, null, "Unknown type: $it") }
                }
            }
        }

        if (checkCycles) {
            val edges =
                decls.filterIsInstance<Decl.Record>().associate { record ->
                    record.name to record.unguardedRefs().flatMap { n -> edgeTargets(n, distinct.associateBy { it.name }) }
                }
            recordCycles(edges).forEach {
                add(it.first(), null, "Cyclic type definition: ${it.joinToString(" -> ")}")
            }
        }
    }
