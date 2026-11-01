package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.idl.Type

interface PrettySyntax {
    fun pretty(): String
}

sealed interface TypeExpr : PrettySyntax {
    val offset: Int

    data class Primitive(
        val type: Type,
        override val offset: Int,
    ) : TypeExpr {
        override fun pretty(): String = type.kind
    }

    data class Named(
        val name: String,
        override val offset: Int,
    ) : TypeExpr {
        override fun pretty(): String = name
    }

    data class Option(
        val inner: TypeExpr,
        override val offset: Int,
    ) : TypeExpr {
        override fun pretty(): String = "option<${inner.pretty()}>"
    }

    data class Seq(
        val inner: TypeExpr,
        override val offset: Int,
    ) : TypeExpr {
        override fun pretty(): String = "seq<${inner.pretty()}>"
    }
}

data class FieldSyntax(
    val name: String,
    val offset: Int,
    val type: TypeExpr,
) : PrettySyntax {
    override fun pretty(): String = "$name: ${type.pretty()} @$offset"
}

data class MemberSyntax(
    val name: String,
    val offset: Int,
    val tag: Int,
    val tagOffset: Int?,
) : PrettySyntax {
    override fun pretty(): String = "$name = $tag @$offset${tagOffset?.let { " @$it" } ?: ""}"
}

sealed interface DeclSyntax : PrettySyntax {
    val name: String
    val offset: Int

    data class Record(
        override val name: String,
        override val offset: Int,
        val fields: List<FieldSyntax>,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("record $name {")
                for ((name, offset, type) in fields) {
                    appendLine("  $name: ${type.pretty()} @$offset")
                }
                appendLine("}")
            }
    }

    data class Enum(
        override val name: String,
        override val offset: Int,
        val members: List<MemberSyntax>,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("enum $name {")
                for ((name, offset, tag, tagOffset) in members) {
                    appendLine("  $name = $tag @$offset${tagOffset?.let { " @$it" } ?: ""}")
                }
                appendLine("}")
            }
    }

    data class Alias(
        override val name: String,
        override val offset: Int,
        val type: TypeExpr,
    ) : DeclSyntax {
        override fun pretty(): String =
            buildString {
                appendLine("alias $name = ${type.pretty()} @$offset")
            }
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
}
