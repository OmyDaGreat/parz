package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Schema
import xyz.malefic.parz.idl.Type

interface PrettySyntax {
    fun pretty(): String
}

sealed interface TypeExpr : PrettySyntax {
    val span: SourceSpan

    fun toType(): Type

    data class Primitive(
        val type: Type,
        override val span: SourceSpan,
    ) : TypeExpr {
        override fun pretty(): String = type.kind

        override fun toType() = type
    }

    data class Named(
        val name: String,
        override val span: SourceSpan,
    ) : TypeExpr {
        override fun pretty(): String = name

        override fun toType() = Type.Ref(name)
    }

    data class Option(
        val inner: TypeExpr,
        override val span: SourceSpan,
    ) : TypeExpr {
        override fun pretty(): String = "option<${inner.pretty()}>"

        override fun toType() = Type.Option(inner.toType())
    }

    data class Seq(
        val inner: TypeExpr,
        override val span: SourceSpan,
    ) : TypeExpr {
        override fun pretty(): String = "seq<${inner.pretty()}>"

        override fun toType() = Type.Seq(inner.toType())
    }
}

data class FieldSyntax(
    val name: String,
    val span: SourceSpan,
    val type: TypeExpr,
) : PrettySyntax {
    override fun pretty(): String = "$name: ${type.pretty()} @$span"
}

data class MemberSyntax(
    val name: String,
    val span: SourceSpan,
    val tag: Int,
    val tagSpan: SourceSpan?,
) : PrettySyntax {
    override fun pretty(): String = "$name = $tag @$span${tagSpan?.let { " @$it" } ?: ""}"
}

sealed interface DeclSyntax : PrettySyntax {
    val name: String
    val span: SourceSpan

    fun toDecl(): Decl

    data class Record(
        override val name: String,
        override val span: SourceSpan,
        val fields: List<FieldSyntax>,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("record $name {")
                for ((name, span, type) in fields) {
                    appendLine("  $name: ${type.pretty()} @$span")
                }
                appendLine("}")
            }

        override fun toDecl() = Decl.Record(name, fields.map { Decl.Record.Field(it.name, it.type.toType()) })
    }

    data class Enum(
        override val name: String,
        override val span: SourceSpan,
        val members: List<MemberSyntax>,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("enum $name {")
                for ((name, span, tag, tagSpan) in members) {
                    appendLine("  $name = $tag @$span${tagSpan?.let { " @$it" } ?: ""}")
                }
                appendLine("}")
            }

        override fun toDecl() = Decl.Enum(name, members.map { Decl.Enum.Member(it.name, it.tag) })
    }

    data class Alias(
        override val name: String,
        override val span: SourceSpan,
        val type: TypeExpr,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("alias $name = ${type.pretty()} @$span")
            }

        override fun toDecl() = Decl.Alias(name, type.toType())
    }
}

data class SchemaSyntax(
    val decls: List<DeclSyntax>,
) : PrettySyntax {
    override fun pretty(): String =
        buildString {
            for (decl in decls) {
                appendLine(decl.pretty())
            }
        }

    fun toSchema() = Schema(decls.map { it.toDecl() })
}
