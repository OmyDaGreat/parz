package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.model.SchemaErrorKind
import xyz.malefic.parz.idl.model.SchemaErrorKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.validate

/*
 * Validating syntax against the model, and mapping the model's errors back to the source spans they came from.
 */

/**
 * Lowers this schema and validates it together with the already accepted [existing] declarations.
 *
 * Every new declaration with a schema error is rejected and its errors are reported at their spans; validation then repeats
 * without it, so declarations that only failed because of a rejected one are rejected too.
 *
 * @return Each accepted declaration paired with its syntax.
 */
context(sink: Sink<AnyDiagnostic>)
fun SchemaSyntax.accept(existing: List<Decl>): List<Pair<DeclSyntax, Decl>> {
    var pending = decls.mapNotNull { s -> s.toDecl()?.let { s to it } }
    while (true) {
        val problems = validate(existing + pending.map { it.second })
        val rejected =
            pending.filter { (_, decl) ->
                problems.diagnostics.any {
                    it.kind.decl === decl || (it.kind as? SchemaErrorKind.CyclicType)?.path?.contains(decl.name) == true
                }
            }
        if (rejected.isEmpty()) return pending
        problems.diagnostics
            .filter { p -> rejected.any { it.second === p.kind.decl } }
            .forEach { sink.report(it.copy(span = pending.locate(it.kind))) }
        pending = pending - rejected.toSet()
    }
}

/**
 * The syntax of the type at [path] in this declaration, or the aliased type if [path] is null.
 */
private fun DeclSyntax.typeAt(
    decl: Decl,
    path: SchemaErrorKind.FieldPath?,
): TypeSyntax? =
    when {
        path == null -> {
            (this as? DeclSyntax.Alias)?.type
        }

        decl is Decl.Record -> {
            (this as DeclSyntax.Record).fields.getOrNull(decl.fields.indexOfFirst { it === path.field })?.type
        }

        decl is Decl.Variant -> {
            val im = decl.members.indexOfFirst { it === (path as VariantPath).member }
            val ip = decl.members[im].payload.indexOfFirst { it === path.field }
            (this as DeclSyntax.Variant)
                .members[im]
                .payload
                .getOrNull(ip)
                ?.type
        }

        else -> {
            null
        }
    }

/**
 * The span in the source of the problem a schema error points at, given each declaration paired with its syntax.
 */
fun List<Pair<DeclSyntax, Decl>>.locate(kind: SchemaErrorKind): SourceSpan? {
    val syntax = firstOrNull { it.second === kind.decl }?.first ?: return null
    return when (kind) {
        is SchemaErrorKind.UnknownType -> {
            syntax.typeAt(kind.decl, kind.path)?.find(kind.name)
        }

        is SchemaErrorKind.InvalidMapKey -> {
            syntax.typeAt(kind.decl, kind.path)?.let { it.findMapKey(kind.key) ?: it.span }
        }

        is SchemaErrorKind.DuplicateFieldName -> {
            when (val decl = kind.decl) {
                is Decl.Record -> {
                    val i = decl.fields.indexOfFirst { it === kind.path.field }
                    (syntax as DeclSyntax.Record).fields[i].span
                }

                is Decl.Variant -> {
                    val im = decl.members.indexOfFirst { it === (kind.path as VariantPath).member }
                    val ip = decl.members[im].payload.indexOfFirst { it === kind.path.field }
                    (syntax as DeclSyntax.Variant)
                        .members[im]
                        .payload[ip]
                        .span
                }

                is Decl.Alias -> {
                    null
                }
            }
        }

        is SchemaErrorKind.MemberError -> {
            val i = (kind.decl as Decl.Variant).members.indexOfFirst { it === kind.member }
            val m = (syntax as DeclSyntax.Variant).members.getOrNull(i) ?: return syntax.span
            if (kind is SchemaErrorKind.MemberError.DuplicateMemberTag) m.tagSpan ?: m.span else m.span
        }

        is SchemaErrorKind.CyclicType, is SchemaErrorKind.DuplicateDecl -> {
            syntax.span
        }
    }
}

/**
 * Finds the span of a given named ref in this type.
 */
internal fun TypeSyntax.find(name: String): SourceSpan? =
    when (this) {
        is TypeSyntax.Named -> if (this.name == name) span else null
        is TypeSyntax.Option -> inner.find(name)
        is TypeSyntax.Seq -> inner.find(name)
        is TypeSyntax.Map -> key.find(name) ?: value.find(name)
        else -> null
    }

/**
 * Finds the span of the first map key in this type that lowers to [key].
 */
internal fun TypeSyntax.findMapKey(key: Type): SourceSpan? =
    when (this) {
        is TypeSyntax.Option -> inner.findMapKey(key)
        is TypeSyntax.Seq -> inner.findMapKey(key)
        is TypeSyntax.Map -> if (this.key.toType() == key) this.key.span else this.key.findMapKey(key) ?: value.findMapKey(key)
        else -> null
    }
