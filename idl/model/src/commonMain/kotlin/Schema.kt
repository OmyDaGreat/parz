package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Checked
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.Decl.Enum.Member
import xyz.malefic.parz.idl.Decl.Record.Field
import xyz.malefic.parz.idl.SchemaErrorKind.CyclicType
import xyz.malefic.parz.idl.SchemaErrorKind.DuplicateDecl
import xyz.malefic.parz.idl.SchemaErrorKind.DuplicateFieldName
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberName
import xyz.malefic.parz.idl.SchemaErrorKind.MemberError.DuplicateMemberTag
import xyz.malefic.parz.idl.SchemaErrorKind.UnknownType
import xyz.malefic.parz.idl.ShapeError.EmptyEnum
import xyz.malefic.parz.idl.ShapeError.EmptyName
import xyz.malefic.parz.idl.ShapeError.EmptyRecord
import xyz.malefic.parz.idl.ShapeError.NegativeTag

sealed interface ShapeError : DiagnosticKind {
    data object EmptyRecord : ShapeError {
        override val code = "IDL120"
        override val message = "Record must have at least one field"

        fun check(fields: List<*>) = listOfNotNull(EmptyRecord.takeIf { fields.isEmpty() })
    }

    data object EmptyEnum : ShapeError {
        override val code = "IDL121"
        override val message = "Enum must have at least one member"

        fun check(members: List<*>) = listOfNotNull(EmptyEnum.takeIf { members.isEmpty() })
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
        override val message = "Enum member tag $tag must be non-negative"

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

    data class Enum(
        override val name: String,
        val members: List<Member>,
    ) : Decl {
        init {
            requireShape(EmptyEnum.check(members))
        }

        data class Member(
            val name: String,
            val tag: Int,
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
                    is Decl.Record -> Resolved.Record(decl)
                    is Decl.Enum -> Resolved.Enum(decl)
                    is Decl.Alias -> resolve(decl.type)
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

    data class UnknownType(
        val name: String,
        override val decl: Decl,
        val field: Field?,
    ) : SchemaErrorKind {
        override val code = "IDL101"
        override val message = "Unknown type${if (field != null) " in field ${field.name}" else ""}: $name"
    }

    data class DuplicateFieldName(
        val field: Field,
        override val decl: Decl,
    ) : SchemaErrorKind {
        override val code = "IDL102"
        override val message = "Duplicate field: ${field.name}"
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

/**
 * Returns null if the type isn't cyclic and returns a `List<String>` of the path to the cycle otherwise.
 */
private fun Decl.Alias.isCyclic(byName: Map<String, Decl>): List<String>? {
    val seen = mutableListOf<String>()
    val pending = ArrayDeque(type.refs())
    while (pending.isNotEmpty()) {
        val name = pending.removeLast()
        if (name == this.name) return seen + name
        if (!seen.add(name)) continue
        (byName[name] as? Decl.Alias)?.let { pending += it.type.refs() }
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

fun edgeTargets(
    name: String,
    declarationMap: Map<String, Decl>,
): List<String> =
    when (val decl = declarationMap[name]) {
        is Decl.Record -> listOf(name)
        is Decl.Alias -> decl.type.unguardedRefs().flatMap { edgeTargets(it, declarationMap) }
        is Decl.Enum, null -> emptyList()
    }

fun validate(decls: List<Decl>): Checked<Unit, Diagnostic<SchemaErrorKind>> =
    collecting {
        fun report(kind: SchemaErrorKind) = contextOf<Sink<Diagnostic<SchemaErrorKind>>>().report(Diagnostic(kind, null))

        val distinct = decls.distinctDecls()
        val declarations = distinct.mapTo(HashSet()) { it.name }
        val declarationMap = distinct.associateBy { it.name }
        var checkRecordCycles = true
        distinct.forEach { decl ->
            when (decl) {
                is Decl.Record -> {
                    decl.fields.forEach { field ->
                        field.type
                            .refs()
                            .filter { it !in declarations }
                            .forEach {
                                report(UnknownType(it, decl, field))
                            }
                    }
                    val fieldNames = mutableSetOf<String>()
                    decl.fields.forEach { field ->
                        if (!fieldNames.add(field.name.generatorDistinct())) {
                            report(DuplicateFieldName(field, decl))
                        }
                    }
                }

                is Decl.Enum -> {
                    val memberNames = mutableSetOf<String>()
                    val memberTags = mutableSetOf<Int>()
                    decl.members.forEach { member ->
                        if (!memberNames.add(member.name.generatorDistinct())) {
                            report(DuplicateMemberName(member.name, decl, member))
                        }
                        if (!memberTags.add(member.tag)) {
                            report(DuplicateMemberTag(member.tag, decl, member))
                        }
                    }
                }

                is Decl.Alias -> {
                    decl.isCyclic(declarationMap)?.let {
                        report(CyclicType(it, decl))
                        checkRecordCycles = false
                    }
                    decl.type
                        .refs()
                        .filter { it !in declarations }
                        .forEach { report(UnknownType(it, decl, null)) }
                }
            }
        }

        if (checkRecordCycles) {
            val edges =
                decls.filterIsInstance<Decl.Record>().associate { record ->
                    record.name to record.unguardedRefs().flatMap { n -> edgeTargets(n, declarationMap) }
                }
            recordCycles(edges).forEach {
                report(CyclicType(it, decls.first { d -> d.name == it.first() }))
            }
        }
    }
