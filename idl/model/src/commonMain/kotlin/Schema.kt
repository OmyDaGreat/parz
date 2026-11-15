package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
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
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberName
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberTag
import xyz.malefic.parz.idl.SchemaErrorKind.UnknownType
import xyz.malefic.parz.idl.ShapeError.EmptyName
import xyz.malefic.parz.idl.ShapeError.EmptyRecord
import xyz.malefic.parz.idl.ShapeError.EmptyVariant
import xyz.malefic.parz.idl.ShapeError.NegativeTag

sealed interface ShapeError : DiagnosticKind {
    data object EmptyRecord : ShapeError {
        override val code = "IDL120"
        override val message = "Record must have at least one field"

        fun check(fields: List<*>) = listOfNotNull(EmptyRecord.takeIf { fields.isEmpty() })
    }

    data object EmptyVariant : ShapeError {
        override val code = "IDL121"
        override val message = "Variant must have at least one member"

        fun check(members: List<*>) = listOfNotNull(EmptyVariant.takeIf { members.isEmpty() })
    }

    data object EmptyName : ShapeError {
        override val code = "IDL122"
        override val message = "Name must not be empty"

        fun check(name: String) = listOfNotNull(EmptyName.takeIf { name.isBlank() })
    }

    data class NegativeTag(
        val tag: Int,
    ) : ShapeError {
        override val code = "IDL123"
        override val message = "Variant member tag $tag must be non-negative"

        companion object {
            fun check(tag: Int) = listOfNotNull(NegativeTag(tag).takeIf { tag < 0 })
        }
    }

    data class EmptyRange(
        val min: BigInteger,
        val max: BigInteger,
    ) : ShapeError {
        override val code = "IDL124"
        override val message = "Range must not be empty or negative: [$min, $max]"

        companion object {
            fun check(
                min: BigInteger,
                max: BigInteger,
            ) = listOfNotNull(EmptyRange(min, max).takeIf { min > max })
        }
    }

    data class NonPositiveLength(
        val maxLen: Long,
    ) : ShapeError {
        override val code = "IDL125"
        override val message = "Length $maxLen must be positive"

        companion object {
            fun check(maxLen: Long) = listOfNotNull(NonPositiveLength(maxLen).takeIf { maxLen <= 0 })
        }
    }

    data class EnumPayload(
        val member: Member,
    ) : ShapeError {
        override val code = "IDL126"
        override val message = "Payload of variant member '${member.name}' must be empty when declared as enum"

        companion object {
            fun check(member: Member) = listOfNotNull(EnumPayload(member).takeIf { member.payload.isNotEmpty() })
        }
    }

    companion object {
        context(sink: Sink<AnyDiagnostic>)
        fun List<ShapeError>.reportAt(span: SourceSpan?) = isEmpty().also { forEach { sink.report(Diagnostic(it, span)) } }
    }
}

fun requireShape(errors: List<ShapeError>) = require(errors.isEmpty()) { "Shape validation failed: ${errors.joinToString { it.message }}" }

sealed interface Decl {
    val name: String

    data class Record(
        override val name: String,
        val fields: List<Field>,
    ) : Decl {
        init {
            requireShape(EmptyRecord.check(fields))
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
                EmptyVariant.check(members) + if (isEnum) members.flatMap { ShapeError.EnumPayload.check(it) } else emptyList(),
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
    ) : Decl
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
}

context(sink: Sink<Diagnostic<SchemaErrorKind>>)
fun List<Decl>.distinctDecls(): List<Decl> {
    val set = HashSet<String>()
    val list = ArrayList<Decl>()
    for (e in this) {
        val key = e.name.generatorDistinct()
        if (set.add(key)) list.add(e) else sink.report(Diagnostic(DuplicateDecl(e), null))
    }
    return list
}

fun Type.refs(): Set<String> =
    when (this) {
        is Type.Ref -> setOf(name)
        is Type.Option -> inner.refs()
        is Type.Seq -> inner.refs()
        else -> emptySet()
    }

fun Type.unguardedRefs(): Set<String> =
    when (this) {
        is Type.Ref -> setOf(name)
        else -> emptySet()
    }

fun Record.unguardedRefs(): Set<String> = fields.flatMapTo(HashSet()) { it.type.unguardedRefs() }

private fun Set<Set<String>>.intersect(): Set<String> = if (isEmpty()) emptySet() else fold(first()) { acc, list -> acc.intersect(list) }

fun Variant.unguardedRefs(): Set<String> =
    members
        .mapTo(HashSet()) { member ->
            member.payload
                .flatMapTo(HashSet()) {
                    it.type.unguardedRefs()
                }
        }.intersect()

/**
 * Returns null if the type isn't cyclic and returns a `List<String>` of the path to the cycle otherwise.
 */
private fun Alias.isCyclic(byName: Map<String, Decl>): List<String>? {
    val seen = mutableListOf<String>()
    val pending = ArrayDeque(type.refs())
    while (pending.isNotEmpty()) {
        val name = pending.removeLast()
        if (name == this.name) return seen + name
        if (!seen.add(name)) continue
        (byName[name] as? Alias)?.let { pending += it.type.refs() }
    }
    return null
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

fun Decl.unguardedEdges(): Set<String> =
    when (this) {
        is Record -> unguardedRefs()
        is Alias -> type.unguardedRefs()
        is Variant -> unguardedRefs()
    }

/** Rotates a closed cycle (`[a, b, c, a]`) so it starts at the first node matching [predicate]. */
private fun List<String>.rotateCycleTo(predicate: (String) -> Boolean): List<String> {
    val open = dropLast(1)
    val start = open.indexOfFirst(predicate).takeIf { it >= 0 } ?: return this
    return open.drop(start) + open.take(start) + open[start]
}

context(sink: Sink<Diagnostic<SchemaErrorKind>>)
private fun checkFieldDecls(
    decl: Decl,
    fields: List<Field>,
    declarations: Set<String>,
    pathOf: (Field) -> SchemaErrorKind.FieldPath,
) {
    fun report(kind: SchemaErrorKind) = sink.report(Diagnostic(kind, null))
    val fieldNames = mutableSetOf<String>()
    fields.forEach { field ->
        field.type
            .refs()
            .filter { it !in declarations }
            .forEach {
                report(UnknownType(it, decl, pathOf(field)))
            }
        if (!fieldNames.add(field.name.generatorDistinct())) {
            report(DuplicateFieldName(pathOf(field), decl))
        }
    }
}

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
        var secondRoundCycles = true
        distinct.forEach { decl ->
            when (decl) {
                is Record -> {
                    checkFieldDecls(decl, decl.fields, declarations, ::RecordPath)
                }

                is Variant -> {
                    val memberNames = mutableSetOf<String>()
                    val memberTags = mutableSetOf<Int>()
                    decl.members.forEach { member ->
                        checkFieldDecls(decl, member.payload, declarations) { VariantPath(member, it) }
                        if (!memberNames.add(member.name.generatorDistinct())) {
                            report(DuplicateMemberName(member.name, decl, member))
                        }
                        if (!memberTags.add(member.tag)) {
                            report(DuplicateMemberTag(member.tag, decl, member))
                        }
                    }
                }

                is Alias -> {
                    decl.isCyclic(declarationMap)?.let {
                        report(CyclicType(it, decl))
                        secondRoundCycles = false
                    }
                    decl.type
                        .refs()
                        .filter { it !in declarations }
                        .forEach { report(UnknownType(it, decl, null)) }
                }
            }
        }

        if (secondRoundCycles) {
            val edges =
                distinct.associate { decl ->
                    decl.name to decl.unguardedEdges().filter { it in declarationMap }
                }
            recordCycles(edges).forEach { cycle ->
                val path = cycle.rotateCycleTo { declarationMap[it] is Record }
                report(CyclicType(path, declarationMap.getValue(path.first())))
            }
        }
    }
