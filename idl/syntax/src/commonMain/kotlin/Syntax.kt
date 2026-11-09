package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Schema
import xyz.malefic.parz.idl.SchemaErrorKind
import xyz.malefic.parz.idl.ShapeError
import xyz.malefic.parz.idl.ShapeError.Companion.reportAt
import xyz.malefic.parz.idl.ShapeError.EmptyEnum
import xyz.malefic.parz.idl.ShapeError.EmptyName
import xyz.malefic.parz.idl.ShapeError.EmptyRecord
import xyz.malefic.parz.idl.ShapeError.NegativeTag
import xyz.malefic.parz.idl.Type

interface PrettySyntax {
    val span: SourceSpan

    fun pretty(): String
}

sealed interface TypeSyntax : PrettySyntax {
    override val span: SourceSpan

    fun toType(): Type

    data class Primitive(
        val type: Type,
        override val span: SourceSpan,
    ) : TypeSyntax {
        override fun pretty(): String =
            when (type) {
                is Type.Decimal -> "decimal(${type.min}, ${type.max}, ${type.scale}, ${type.rounding}, ${type.overflow})"
                is Type.Str -> "string(${type.maxLen})"
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

        override fun toType() = Type.Option(inner.toType())
    }

    data class Seq(
        val inner: TypeSyntax,
        override val span: SourceSpan,
    ) : TypeSyntax {
        override fun pretty(): String = "seq<${inner.pretty()}>"

        override fun toType() = Type.Seq(inner.toType())
    }

    fun find(name: String): SourceSpan? =
        when (this) {
            is Named -> if (this.name == name) span else null
            is Option -> inner.find(name)
            is Seq -> inner.find(name)
            else -> null
        }
}

data class FieldSyntax(
    val name: String,
    override val span: SourceSpan,
    val type: TypeSyntax,
) : PrettySyntax {
    override fun pretty(): String = "$name: ${type.pretty()} $span"
}

data class MemberSyntax(
    val name: String,
    override val span: SourceSpan,
    val tag: Int,
    val tagSpan: SourceSpan?,
) : PrettySyntax {
    override fun pretty(): String = "$name = $tag"
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
                for ((name, _, type) in fields) {
                    appendLine("  $name: ${type.pretty()}")
                }
                appendLine("}")
            }

        context(sink: Sink<AnyDiagnostic>)
        override fun toDecl(): Decl.Record? {
            var ok = EmptyRecord.check(fields).reportAt(span)
            for ((name, span) in fields) ok = EmptyName.check(name).reportAt(span) && ok
            return if (ok) Decl.Record(name, fields.map { Decl.Record.Field(it.name, it.type.toType()) }) else null
        }
    }

    data class Enum(
        override val name: String,
        override val span: SourceSpan,
        val members: List<MemberSyntax>,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("enum $name {")
                for ((name, _, tag, _) in members) {
                    appendLine("  $name = $tag")
                }
                appendLine("}")
            }

        context(sink: Sink<AnyDiagnostic>)
        override fun toDecl(): Decl.Enum? {
            var ok = EmptyEnum.check(members).reportAt(span)
            for ((name, span, tag, tagSpan) in members) {
                ok = EmptyName.check(name).reportAt(span) && NegativeTag.check(tag).reportAt(tagSpan) && ok
            }
            return if (ok) Decl.Enum(name, members.map { Decl.Enum.Member(it.name, it.tag) }) else null
        }
    }

    data class Alias(
        override val name: String,
        override val span: SourceSpan,
        val type: TypeSyntax,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("type $name = ${type.pretty()}")
            }

        context(sink: Sink<AnyDiagnostic>)
        override fun toDecl() = Decl.Alias(name, type.toType())
    }

    companion object {
        fun List<Pair<DeclSyntax, Decl>>.locate(kind: SchemaErrorKind): SourceSpan? {
            val syntax = firstOrNull { it.second === kind.decl }?.first ?: return null
            return when (kind) {
                is SchemaErrorKind.UnknownType -> {
                    val field = kind.field ?: return (syntax as Alias).type.find(kind.name)
                    val i = (kind.decl as Decl.Record).fields.indexOfFirst { it === field }
                    (syntax as Record).fields[i].type.find(kind.name)
                }

                is SchemaErrorKind.DuplicateFieldName -> {
                    val i = (kind.decl as Decl.Record).fields.indexOfFirst { it === kind.field }
                    (syntax as Record).fields[i].span
                }

                is SchemaErrorKind.MemberError -> {
                    val i = (kind.decl as Decl.Enum).members.indexOfFirst { it === kind.member }
                    val m = (syntax as Enum).members.getOrNull(i) ?: return syntax.span
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

    override fun pretty(): String =
        buildString {
            for (decl in decls) {
                appendLine(decl.pretty())
            }
        }
}
