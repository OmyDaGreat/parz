package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.SchemaErrorKind
import xyz.malefic.parz.idl.SchemaErrorKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.ShapeErrorKind
import xyz.malefic.parz.idl.ShapeErrorKind.Companion.reportAt
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyName
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyRecord
import xyz.malefic.parz.idl.ShapeErrorKind.EmptyVariant
import xyz.malefic.parz.idl.ShapeErrorKind.NegativeTag
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Primitive.Decimal
import xyz.malefic.parz.idl.Type.Primitive.Str

interface PrettySyntax {
    val span: SourceSpan

    fun pretty(): String
}

sealed interface TypeSyntax : PrettySyntax {
    override val span: SourceSpan

    fun toType(): Type

    data class Primitive(
        val type: Type.Primitive,
        override val span: SourceSpan,
    ) : TypeSyntax {
        override fun pretty(): String =
            when (type) {
                is Decimal -> "decimal(${type.min}..${type.max}, ${type.scale}, ${type.rounding}, ${type.overflow})"
                is Str -> "string(${type.maxLen})"
                else -> type.kind
            }

        override fun toType() = type
    }

    data class Named(
        val name: String,
        override val span: SourceSpan,
    ) : TypeSyntax {
        override fun pretty(): String = name

        override fun toType() = Type.Ref(name)
    }

    data class Option(
        val inner: TypeSyntax,
        override val span: SourceSpan,
    ) : TypeSyntax {
        override fun pretty(): String = "option<${inner.pretty()}>"

        override fun toType() = Type.Collection.Option(inner.toType())
    }

    data class Seq(
        val inner: TypeSyntax,
        override val span: SourceSpan,
    ) : TypeSyntax {
        override fun pretty(): String = "seq<${inner.pretty()}>"

        override fun toType() = Type.Collection.Seq(inner.toType())
    }

    data class Map(
        val key: TypeSyntax,
        val value: TypeSyntax,
        override val span: SourceSpan,
    ) : TypeSyntax {
        override fun pretty(): String = "map<${key.pretty()}, ${value.pretty()}>"

        override fun toType() = Type.Collection.Map(key.toType(), value.toType())
    }

    /**
     * Finds the span of a given named ref in this type.
     */
    fun find(name: String): SourceSpan? =
        when (this) {
            is Named -> if (this.name == name) span else null
            is Option -> inner.find(name)
            is Seq -> inner.find(name)
            is Map -> key.find(name) ?: value.find(name)
            else -> null
        }
}

data class FieldSyntax(
    val name: String,
    override val span: SourceSpan,
    val type: TypeSyntax,
) : PrettySyntax {
    override fun pretty(): String = "$name: ${type.pretty()}"

    fun toField() = Decl.Record.Field(name, type.toType())
}

data class MemberSyntax(
    val name: String,
    override val span: SourceSpan,
    val tag: Int,
    val tagSpan: SourceSpan?,
    val payload: List<FieldSyntax> = emptyList(),
) : PrettySyntax {
    override fun pretty(): String =
        "$name${payload.takeIf { it.isNotEmpty() }?.let { fields -> " (${ fields.joinToString(", ") { it.pretty() } })" } ?: ""} = $tag"

    fun toMember() = Decl.Variant.Member(name, tag, payload.map { it.toField() })
}

sealed interface DeclSyntax : PrettySyntax {
    val name: String
    override val span: SourceSpan

    context(sink: Sink<AnyDiagnostic>)
    fun toDecl(): Decl?

    data class Record(
        override val name: String,
        override val span: SourceSpan,
        val fields: List<FieldSyntax>,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("record $name {")
                fields.forEach { field -> appendLine("  ${field.pretty()},") }
                append("}")
            }

        context(sink: Sink<AnyDiagnostic>)
        override fun toDecl(): Decl.Record? {
            var ok = EmptyName.check(name).reportAt(span)
            ok = EmptyRecord.check(fields).reportAt(span) && ok
            for ((name, span) in fields) ok = EmptyName.check(name).reportAt(span) && ok
            return if (ok) Decl.Record(name, fields.map { it.toField() }) else null
        }
    }

    data class Variant(
        override val name: String,
        override val span: SourceSpan,
        val members: List<MemberSyntax>,
        val isEnum: Boolean = false,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("${if (isEnum) "enum" else "variant"} $name {")
                members.forEach { appendLine("  ${it.pretty()},") }
                append("}")
            }

        context(sink: Sink<AnyDiagnostic>)
        override fun toDecl(): Decl.Variant? {
            var ok = EmptyName.check(name).reportAt(span)
            ok = EmptyVariant.check(members).reportAt(span) && ok
            members.forEach {
                ok = EmptyName.check(it.name).reportAt(it.span) && ok
                ok = NegativeTag.check(it.tag).reportAt(it.tagSpan) && ok
                if (isEnum) {
                    ok = ShapeErrorKind.EnumPayload.check(it.toMember()).reportAt(it.span) && ok
                }
            }
            return if (ok) {
                Decl.Variant(name, members.map { it.toMember() }, isEnum)
            } else {
                null
            }
        }
    }

    data class Alias(
        override val name: String,
        override val span: SourceSpan,
        val type: TypeSyntax,
    ) : DeclSyntax {
        override fun pretty(): String = "type $name = ${type.pretty()}"

        context(sink: Sink<AnyDiagnostic>)
        override fun toDecl() = if (EmptyName.check(name).reportAt(span)) Decl.Alias(name, type.toType()) else null
    }

    companion object {
        fun List<Pair<DeclSyntax, Decl>>.locate(kind: SchemaErrorKind): SourceSpan? {
            val syntax = firstOrNull { it.second === kind.decl }?.first ?: return null
            return when (kind) {
                is SchemaErrorKind.UnknownType -> {
                    val path = kind.path ?: return (syntax as Alias).type.find(kind.name)
                    when (val decl = kind.decl) {
                        is Decl.Record -> {
                            val i = decl.fields.indexOfFirst { it === path.field }
                            (syntax as Record).fields[i].type.find(kind.name)
                        }

                        is Decl.Variant -> {
                            val im = decl.members.indexOfFirst { it === (path as VariantPath).member }
                            val ip = decl.members[im].payload.indexOfFirst { it === path.field }
                            (syntax as Variant)
                                .members[im]
                                .payload[ip]
                                .type
                                .find(kind.name)
                        }

                        is Decl.Alias -> {
                            (syntax as Alias).type.find(kind.name)
                        }
                    }
                }

                is SchemaErrorKind.DuplicateFieldName -> {
                    when (val decl = kind.decl) {
                        is Decl.Record -> {
                            val i = decl.fields.indexOfFirst { it === kind.path.field }
                            (syntax as Record).fields[i].span
                        }

                        is Decl.Variant -> {
                            val im = decl.members.indexOfFirst { it === (kind.path as VariantPath).member }
                            val ip = decl.members[im].payload.indexOfFirst { it === kind.path.field }
                            (syntax as Variant)
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
                    val m = (syntax as Variant).members.getOrNull(i) ?: return syntax.span
                    if (kind is SchemaErrorKind.MemberError.DuplicateMemberTag) m.tagSpan ?: m.span else m.span
                }

                is SchemaErrorKind.CyclicType, is SchemaErrorKind.DuplicateDecl -> {
                    syntax.span
                }
            }
        }
    }
}

data class SchemaSyntax(
    val decls: List<DeclSyntax>,
) : PrettySyntax {
    override val span: SourceSpan = if (decls.isEmpty()) SourceSpan(0, 0) else SourceSpan(decls.first().span.start, decls.last().span.end)

    override fun pretty(): String = decls.joinToString("\n\n") { it.pretty() }
}
