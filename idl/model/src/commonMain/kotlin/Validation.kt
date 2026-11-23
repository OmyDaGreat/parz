package xyz.malefic.parz.idl.model

import xyz.malefic.parz.core.Checked
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Errors
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.model.Decl.Alias
import xyz.malefic.parz.idl.model.Decl.Record
import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant
import xyz.malefic.parz.idl.model.SchemaErrorKind.CyclicType
import xyz.malefic.parz.idl.model.SchemaErrorKind.DuplicateDecl
import xyz.malefic.parz.idl.model.SchemaErrorKind.DuplicateFieldName
import xyz.malefic.parz.idl.model.SchemaErrorKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.model.SchemaErrorKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.model.SchemaErrorKind.InvalidMapKey
import xyz.malefic.parz.idl.model.SchemaErrorKind.InvalidTarget
import xyz.malefic.parz.idl.model.SchemaErrorKind.MemberError.DuplicateMemberName
import xyz.malefic.parz.idl.model.SchemaErrorKind.MemberError.DuplicateMemberTag
import xyz.malefic.parz.idl.model.SchemaErrorKind.MissingTable
import xyz.malefic.parz.idl.model.SchemaErrorKind.UnknownType

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
    tables: Set<String>,
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
        checkReferenceTypes(field.type, decl, pathOf(field), declarationMap, tables)
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
        is Type.Collection.Table -> inner.mapKeys()
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
 * Every `id<…>` and `table<…>` in this type, including ones nested in other types.
 */
private fun Type.references(): List<Type> =
    when (this) {
        is Type.Primitive.Id -> listOf(this)
        is Type.Collection.Table -> listOf(this) + inner.references()
        is Type.Collection.Option -> inner.references()
        is Type.Collection.Seq -> inner.references()
        is Type.Collection.Map -> keys.references() + values.references()
        else -> emptyList()
    }

/**
 * Every type written in this declaration: its fields, member payloads, or aliased type.
 */
private fun Decl.types(): List<Type> =
    when (this) {
        is Record -> fields.map { it.type }
        is Variant -> members.flatMap { m -> m.payload.map { it.type } }
        is Alias -> listOf(type)
    }

/**
 * The names of every declaration some `table<…>` in [decls] holds.
 */
private fun tableTargets(decls: List<Decl>): Set<String> =
    decls
        .flatMap { it.types() }
        .flatMap { it.references() }
        .filterIsInstance<Type.Collection.Table>()
        .mapNotNullTo(HashSet()) { (it.inner as? Type.Ref)?.name }

/**
 * Validates every `id<…>` and `table<…>` in a type:
 * - Its target must name a record or variant directly ([InvalidTarget]); unknown names are left to [UnknownType]
 * - Every `id<T>` needs a `table<T>` somewhere in the schema ([MissingTable])
 */
context(sink: Sink<Diagnostic<SchemaErrorKind>>)
private fun checkReferenceTypes(
    type: Type,
    decl: Decl,
    path: SchemaErrorKind.FieldPath?,
    declarationMap: Map<String, Decl>,
    tables: Set<String>,
) = type.references().forEach { ref ->
    val target =
        when (ref) {
            is Type.Primitive.Id -> ref.target
            is Type.Collection.Table -> (ref.inner as? Type.Ref)?.name
            else -> null
        }
    val kind =
        when {
            target == null -> InvalidTarget(ref, decl, path)
            target !in declarationMap -> null
            declarationMap.getValue(target) is Alias -> InvalidTarget(ref, decl, path)
            ref is Type.Primitive.Id && target !in tables -> MissingTable(target, decl, path)
            else -> null
        }
    kind?.let { sink.report(Diagnostic(it, null)) }
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
        val reportedCycles = HashSet<String>()
        val tables = tableTargets(distinct)
        distinct.forEach { decl ->
            when (decl) {
                is Record -> {
                    checkFieldDecls(decl, decl.fields, declarationMap, tables, ::RecordPath)
                }

                is Variant -> {
                    val memberNames = mutableSetOf<String>()
                    val memberTags = mutableSetOf<Int>()
                    decl.members.forEach { member ->
                        checkFieldDecls(decl, member.payload, declarationMap, tables) { VariantPath(member, it) }
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
                    checkReferenceTypes(decl.type, decl, null, declarationMap, tables)
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

fun Type.refs(): Set<String> =
    when (this) {
        is Type.Ref -> setOf(name)
        is Type.Primitive.Id -> setOf(target)
        is Type.Collection.Option -> inner.refs()
        is Type.Collection.Seq -> inner.refs()
        is Type.Collection.Map -> keys.refs() + values.refs()
        is Type.Collection.Table -> inner.refs()
        else -> emptySet()
    }

fun String.generatorDistinct() = replace("_", "").lowercase()
