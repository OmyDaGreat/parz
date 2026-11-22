package xyz.malefic.parz.idl.model

import xyz.malefic.parz.idl.model.Decl.Alias
import xyz.malefic.parz.idl.model.Decl.Record
import xyz.malefic.parz.idl.model.Decl.Variant

private fun Type.buildable(
    known: Set<String>,
    declared: Set<String>,
) = this !is Type.Ref || name in known || name !in declared

private fun Decl.buildable(
    known: Set<String>,
    declared: Set<String>,
) = when (this) {
    is Record -> fields.all { it.type.buildable(known, declared) }
    is Variant -> members.any { m -> m.payload.all { it.type.buildable(known, declared) } }
    is Alias -> type.buildable(known, declared)
}

/**
 * Returns the names of every declaration a finite value can be built for.
 * @param decls The list of declarations to check.
 * @return A set of names of the buildable declarations.
 */
internal fun buildable(decls: List<Decl>): Set<String> {
    val declared = decls.mapTo(HashSet()) { it.name }
    val known = HashSet<String>()
    do {
        val added = decls.filter { it.name !in known && it.buildable(known, declared) }
        added.mapTo(known) { it.name }
    } while (added.isNotEmpty())
    return known
}

/**
 * Returns the name of the first declaration that blocks the creation of a finite value.
 * @param known The set of known declaration names.
 * @param declared The set of declared declaration names.
 * @return The name of the first blocking declaration, or null if none exists.
 */
internal fun Decl.blame(
    known: Set<String>,
    declared: Set<String>,
): String? {
    fun Type.blocked() = (this as? Type.Ref)?.name?.takeIf { it !in known && it in declared }
    return when (this) {
        is Record -> fields.firstNotNullOf { it.type.blocked() }
        is Variant -> members.first().payload.firstNotNullOf { it.type.blocked() }
        is Alias -> type.blocked()
    }
}

/**
 * Returns null if the type isn't cyclic and returns a `List<String>` of the path to the cycle otherwise.
 * @param byName A map of declaration names to declarations.
 * @return A `List<String>` of the path to the cycle, or null if the type isn't cyclic.
 */
internal fun Alias.isCyclic(byName: Map<String, Decl>): List<String>? {
    val seen = LinkedHashSet<String>()
    val pending = ArrayDeque(type.refs())
    while (pending.isNotEmpty()) {
        val name = pending.removeLast()
        if (name == this.name) return listOf(this.name) + seen + name
        if (!seen.add(name)) continue
        (byName[name] as? Alias)?.let { pending += it.type.refs() }
    }
    return null
}

/**
 * Rotates a closed cycle (`[a, b, c, a]`) so it starts at the first node matching [predicate].
 * @param predicate The predicate to match.
 * @return The rotated cycle.
 */
internal fun List<String>.rotateCycleTo(predicate: (String) -> Boolean): List<String> {
    val open = dropLast(1)
    val start = open.indexOfFirst(predicate).takeIf { it >= 0 } ?: return this
    return open.drop(start) + open.take(start) + open[start]
}
