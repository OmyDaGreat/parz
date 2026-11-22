package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Checked
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.core.Errors
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.Decl.Alias
import xyz.malefic.parz.idl.Decl.Record
import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.Decl.Variant
import xyz.malefic.parz.idl.Decl.Variant.Member
import xyz.malefic.parz.idl.SchemaErrorKind.CyclicType
import xyz.malefic.parz.idl.SchemaErrorKind.DuplicateDecl
import xyz.malefic.parz.idl.SchemaErrorKind.DuplicateFieldName
import xyz.malefic.parz.idl.SchemaErrorKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.SchemaErrorKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.SchemaErrorKind.InvalidMapKey
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberName
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberTag
import xyz.malefic.parz.idl.SchemaErrorKind.UnknownType
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyName
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyRecord
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyVariant
import xyz.malefic.parz.idl.ShapeErrorKind.NegativeTag

sealed interface ShapeErrorKind : DiagnosticKind {
    data object EmptyRecord : ShapeErrorKind {
        override val code = "IDL120"
        override val message = "Record must have at least one field"

        fun check(fields: List<*>) = listOfNotNull(EmptyRecord.takeIf { fields.isEmpty() })
    }

    data object EmptyVariant : ShapeErrorKind {
        override val code = "IDL121"
        override val message = "Variant must have at least one member"

        fun check(members: List<*>) = listOfNotNull(EmptyVariant.takeIf { members.isEmpty() })
    }

    data object EmptyName : ShapeErrorKind {
        override val code = "IDL122"
        override val message = "Name must not be empty"

        fun check(name: String) = listOfNotNull(EmptyName.takeIf { name.isBlank() })
    }

    data class NegativeTag(
        val tag: Int,
    ) : ShapeErrorKind {
        override val code = "IDL123"
        override val message = "Variant member tag $tag must be non-negative"

        companion object {
            fun check(tag: Int) = listOfNotNull(NegativeTag(tag).takeIf { tag < 0 })
        }
    }

    data class EmptyRange(
        val min: BigInteger,
        val max: BigInteger,
    ) : ShapeErrorKind {
        override val code = "IDL124"
        override val message = "Range must not be empty or negative: [$min, $max]"

        companion object {
            fun check(
                min: BigInteger,
                max: BigInteger,
            ) = listOfNotNull(EmptyRange(min, max).takeIf { min > max })

            fun check(
                min: Long,
                max: Long,
            ) = listOfNotNull(EmptyRange(min.toBigInteger(), max.toBigInteger()).takeIf { min > max })
        }
    }

    data class NonPositiveLength(
        val length: Long,
    ) : ShapeErrorKind {
        override val code = "IDL125"
        override val message = "Length $length must be positive"

        companion object {
            fun check(maxLen: Long) = listOfNotNull(NonPositiveLength(maxLen).takeIf { maxLen <= 0 })
        }
    }

    data class EnumPayload(
        val member: Member,
    ) : ShapeErrorKind {
        override val code = "IDL126"
        override val message = "Payload of variant member '${member.name}' must be empty when declared as enum"

        companion object {
            fun check(member: Member) = listOfNotNull(EnumPayload(member).takeIf { member.payload.isNotEmpty() })
        }
    }

    data class NegativeLength(
        val length: Long,
    ) : ShapeErrorKind {
        override val code = "IDL127"
        override val message = "Length $length must be non-negative"

        companion object {
            fun check(length: Long) = listOfNotNull(NegativeLength(length).takeIf { length < 0 })
        }
    }

    companion object {
        /**
         * Reports a list of shape errors.
         * @param span The span to report the errors at.
         * @return `true` if the list is empty, `false` otherwise.
         */
        context(sink: Sink<AnyDiagnostic>)
        fun List<ShapeErrorKind>.reportAt(span: SourceSpan?) = isEmpty().also { forEach { sink.report(Diagnostic(it, span)) } }
    }
}

fun requireShape(errors: List<ShapeErrorKind>) =
    require(errors.isEmpty()) {
        "Shape validation failed: ${errors.joinToString { it.message }}"
    }

sealed interface Decl {
    val name: String

    data class Record(
        override val name: String,
        val fields: List<Field>,
    ) : Decl {
        init {
            requireShape(EmptyName.check(name) + EmptyRecord.check(fields))
        }

        data class Field(
            val name: String,
            val type: Type,
        ) {
            init {
                requireShape(EmptyName.check(name))
            }
        }
    }

    data class Variant(
        override val name: String,
        val members: List<Member>,
        val isEnum: Boolean = false,
    ) : Decl {
        init {
            requireShape(
                EmptyName.check(name) + EmptyVariant.check(members) +
                    if (isEnum) members.flatMap { ShapeErrorKind.EnumPayload.check(it) } else emptyList(),
            )
        }

        data class Member(
            val name: String,
            val tag: Int,
            val payload: List<Field> = emptyList(),
        ) {
            init {
                requireShape(EmptyName.check(name) + NegativeTag.check(tag))
            }
        }
    }

    data class Alias(
        override val name: String,
        val type: Type,
    ) : Decl {
        init {
            requireShape(EmptyName.check(name))
        }
    }
}

sealed interface Resolved {
    data class Record(
        val decl: Decl.Record,
    ) : Resolved

    data class Variant(
        val decl: Decl.Variant,
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
        val problems = validate()
        require(problems.ok) { "Schema validation failed: ${problems.diagnostics.joinToString { it.kind.message }}" }
    }

    companion object {
        val EMPTY = Schema(emptyList())
    }

    operator fun get(name: String) = byName[name]

    fun resolve(type: Type): Resolved =
        when (type) {
            is Type.Ref -> {
                when (val decl = get(type.name) ?: error("Unresolved type reference in schema: ${type.name}")) {
                    is Record -> Resolved.Record(decl)
                    is Variant -> Resolved.Variant(decl)
                    is Alias -> resolve(decl.type)
                }
            }

            else -> {
                Resolved.Other(type)
            }
        }

    fun validate() = validate(decls)
}

sealed interface SchemaErrorKind : DiagnosticKind {
    val decl: Decl

    sealed interface FieldPath {
        val field: Field

        data class RecordPath(
            override val field: Field,
        ) : FieldPath {
            override fun toString() = field.name
        }

        data class VariantPath(
            val member: Member,
            override val field: Field,
        ) : FieldPath {
            override fun toString() = "${member.name}.${field.name}"
        }
    }

    data class UnknownType(
        val name: String,
        override val decl: Decl,
        val path: FieldPath?,
    ) : SchemaErrorKind {
        override val code = "IDL101"
        override val message = "Unknown type${if (path != null) " in field $path" else ""}: $name"
    }

    data class DuplicateFieldName(
        val path: FieldPath,
        override val decl: Decl,
    ) : SchemaErrorKind {
        override val code = "IDL102"
        override val message = "Duplicate field ${path.field.name} in ${decl.name}"
    }

    sealed interface MemberError : SchemaErrorKind {
        val member: Member

        data class DuplicateMemberName(
            val name: String,
            override val decl: Decl,
            override val member: Member,
        ) : MemberError {
            override val code = "IDL103"
            override val message = "Duplicate member name: $name"
        }

        data class DuplicateMemberTag(
            val tag: Int,
            override val decl: Decl,
            override val member: Member,
        ) : MemberError {
            override val code = "IDL104"
            override val message = "Duplicate member tag: $tag"
        }
    }

    data class CyclicType(
        val path: List<String>,
        override val decl: Decl,
    ) : SchemaErrorKind {
        override val code = "IDL105"
        override val message = "Cyclic type definition: ${path.joinToString(" -> ")}"
    }

    data class DuplicateDecl(
        override val decl: Decl,
    ) : SchemaErrorKind {
        override val code = "IDL106"
        override val message = "Duplicate declaration: ${decl.name}"
    }

    /**
     * A map's key type isn't a bool, decimal, string, or variant without payloads (an enum), directly or through aliases.
     */
    data class InvalidMapKey(
        val key: Type,
        override val decl: Decl,
        val path: FieldPath?,
    ) : SchemaErrorKind {
        override val code = "IDL107"
        override val message =
            "Invalid map key${if (path != null) " in field $path" else ""}: ${(key as? Type.Ref)?.name ?: key.kind}" +
                " (keys must be bool, decimal, string, or an enum)"
    }
}

context(sink: Sink<Diagnostic<SchemaErrorKind>>)
private fun List<Decl>.distinctDecls(): List<Decl> {
    val set = HashSet<String>()
    val list = ArrayList<Decl>()
    for (e in this) {
        val key = e.name.generatorDistinct()
        if (set.add(key)) list.add(e) else sink.report(Diagnostic(DuplicateDecl(e), null))
    }
    return list
}

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
fun buildable(decls: List<Decl>): Set<String> {
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
fun Decl.blame(
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

fun Type.refs(): Set<String> =
    when (this) {
        is Type.Ref -> setOf(name)
        is Type.Collection.Option -> inner.refs()
        is Type.Collection.Seq -> inner.refs()
        is Type.Collection.Map -> keys.refs() + values.refs()
        else -> emptySet()
    }

/**
 * Returns null if the type isn't cyclic and returns a `List<String>` of the path to the cycle otherwise.
 * @param byName A map of declaration names to declarations.
 * @return A `List<String>` of the path to the cycle, or null if the type isn't cyclic.
 */
private fun Alias.isCyclic(byName: Map<String, Decl>): List<String>? {
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
private fun List<String>.rotateCycleTo(predicate: (String) -> Boolean): List<String> {
    val open = dropLast(1)
    val start = open.indexOfFirst(predicate).takeIf { it >= 0 } ?: return this
    return open.drop(start) + open.take(start) + open[start]
}

/**
 * Validates the fields of a record. Also calls [checkMapKeys].
 * @param sink The sink to report errors to.
 * @param decl The record declaration.
 * @param fields The fields to validate.
 * @param declarationMap A map of declaration names to declarations.
 * @param pathOf A function that returns the path of a field.
 */
context(sink: Sink<Diagnostic<SchemaErrorKind>>)
private fun checkFieldDecls(
    decl: Decl,
    fields: List<Field>,
    declarationMap: Map<String, Decl>,
    pathOf: (Field) -> SchemaErrorKind.FieldPath,
) {
    fun report(kind: SchemaErrorKind) = sink.report(Diagnostic(kind, null))
    val fieldNames = mutableSetOf<String>()
    fields.forEach { field ->
        field.type
            .refs()
            .filter { it !in declarationMap }
            .forEach {
                report(UnknownType(it, decl, pathOf(field)))
            }
        checkMapKeys(field.type, decl, pathOf(field), declarationMap)
        if (!fieldNames.add(field.name.generatorDistinct())) {
            report(DuplicateFieldName(pathOf(field), decl))
        }
    }
}

/**
 * The key type of every map in this type, including maps nested in options, sequences, and other maps.
 */
private fun Type.mapKeys(): List<Type> =
    when (this) {
        is Type.Collection.Map -> listOf(keys) + keys.mapKeys() + values.mapKeys()
        is Type.Collection.Option -> inner.mapKeys()
        is Type.Collection.Seq -> inner.mapKeys()
        else -> emptyList()
    }

/**
 * Whether this type can key a map: any primitive but void or a ref to an enum variant, directly or through aliases.
 *
 * Unknown refs and alias cycles count aren't dealt with, since they're already reported as [UnknownType] and [CyclicType] elsewhere.
 */
private fun Type.isAllowedKey(
    declarationMap: Map<String, Decl>,
    seen: Set<String> = emptySet(),
): Boolean =
    when (this) {
        is Type.Primitive -> {
            this !is Type.Primitive.Void
        }

        is Type.Collection -> {
            false
        }

        is Type.Ref -> {
            when (val decl = declarationMap[name]) {
                null -> true
                is Record -> false
                is Variant -> decl.members.all { it.payload.isEmpty() }
                is Alias -> name in seen || decl.type.isAllowedKey(declarationMap, seen + name)
            }
        }
    }

/**
 * Validates the keys of a map.
 * @param sink The sink to report errors to.
 * @param type The map type to validate.
 * @param decl The declaration of the map.
 * @param path The path of the map.
 * @param declarationMap A map of declaration names to declarations.
 */
context(sink: Sink<Diagnostic<SchemaErrorKind>>)
private fun checkMapKeys(
    type: Type,
    decl: Decl,
    path: SchemaErrorKind.FieldPath?,
    declarationMap: Map<String, Decl>,
) = type.mapKeys().filterNot { it.isAllowedKey(declarationMap) }.forEach { sink.report(Diagnostic(InvalidMapKey(it, decl, path), null)) }

/**
 * Validates a list of declarations, reporting any errors found.
 * @param decls The declarations to validate
 * @return A list of errors found within the declarations through [Checked]
 */
fun validate(decls: List<Decl>): Errors<Diagnostic<SchemaErrorKind>> =
    collecting {
        fun report(kind: SchemaErrorKind) = contextOf<Sink<Diagnostic<SchemaErrorKind>>>().report(Diagnostic(kind, null))

        val distinct = decls.distinctDecls()
        val declarations = distinct.mapTo(HashSet()) { it.name }
        val declarationMap = distinct.associateBy { it.name }
        val reportedCycles = HashSet<String>()
        distinct.forEach { decl ->
            when (decl) {
                is Record -> {
                    checkFieldDecls(decl, decl.fields, declarationMap, ::RecordPath)
                }

                is Variant -> {
                    val memberNames = mutableSetOf<String>()
                    val memberTags = mutableSetOf<Int>()
                    decl.members.forEach { member ->
                        checkFieldDecls(decl, member.payload, declarationMap) { VariantPath(member, it) }
                        if (!memberNames.add(member.name.generatorDistinct())) {
                            report(DuplicateMemberName(member.name, decl, member))
                        }
                        if (!memberTags.add(member.tag)) {
                            report(DuplicateMemberTag(member.tag, decl, member))
                        }
                    }
                }

                is Alias -> {
                    decl.isCyclic(declarationMap)?.let { it ->
                        if (it.none { it in reportedCycles }) {
                            reportedCycles += it
                            report(CyclicType(it, decl))
                        }
                    }
                    decl.type
                        .refs()
                        .filter { it !in declarations }
                        .forEach { report(UnknownType(it, decl, null)) }
                    checkMapKeys(decl.type, decl, null, declarationMap)
                }
            }
        }

        val known = buildable(distinct)
        for (start in distinct.map { it.name }.filter { it !in known }) {
            val path = mutableListOf<String>()
            var current = start
            while (current !in path) {
                path += current
                current = declarationMap.getValue(current).blame(known, declarations)!!
            }
            val cycle = path.drop(path.indexOf(current)) + current
            if (cycle.none { it in reportedCycles }) {
                reportedCycles += cycle
                val rotated = cycle.rotateCycleTo { declarationMap[it] is Record }
                report(CyclicType(rotated, declarationMap.getValue(rotated.first())))
            }
        }
    }
