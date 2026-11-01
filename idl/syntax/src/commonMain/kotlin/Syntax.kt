package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.idl.Type

sealed interface TypeExpr {
    val offset: Int

    data class Primitive(
        val type: Type,
        override val offset: Int,
    ) : TypeExpr

    data class Named(
        val name: String,
        override val offset: Int,
    ) : TypeExpr

    data class Option(
        val inner: TypeExpr,
        override val offset: Int,
    ) : TypeExpr

    data class Seq(
        val inner: TypeExpr,
        override val offset: Int,
    ) : TypeExpr
}

data class FieldSyntax(
    val name: String,
    val offset: Int,
    val type: TypeExpr,
)

data class MemberSyntax(
    val name: String,
    val offset: Int,
    val tag: Int,
    val tagOffset: Int?,
)

sealed interface DeclSyntax {
    val name: String
    val offset: Int

    data class Record(
        override val name: String,
        override val offset: Int,
        val fields: List<FieldSyntax>,
    ) : DeclSyntax

    data class Enum(
        override val name: String,
        override val offset: Int,
        val members: List<MemberSyntax>,
    ) : DeclSyntax

    data class Alias(
        override val name: String,
        override val offset: Int,
        val type: TypeExpr,
    ) : DeclSyntax
}

data class SchemaSyntax(
    val decls: List<DeclSyntax>,
)
