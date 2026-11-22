package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.model.ShapeErrorKind
import xyz.malefic.parz.idl.model.ShapeErrorKind.Companion.reportAt
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyName
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyRecord
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyVariant
import xyz.malefic.parz.idl.model.ShapeErrorKind.NegativeTag
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Primitive.Bytes
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal
import xyz.malefic.parz.idl.model.Type.Primitive.Str

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
                // TODO: Write decimal in shortest form
                is Decimal -> {
                    "decimal(${type.min}..${type.max}, ${type.scale}, ${type.rounding}, ${type.overflow})"
                }

                is Bytes -> {
                    when {
                        type.maxLen == Long.MAX_VALUE && type.minLen == 0L -> "bytes"
                        type.exact -> "bytes(${type.maxLen})"
                        else -> "bytes(${type.minLen}..${type.maxLen})"
                    }
                }

                is Str -> {
                    if (type.maxLen == Long.MAX_VALUE) "string" else "string(${type.maxLen})"
                }

                else -> {
                    type.kind
                }
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
}

data class SchemaSyntax(
    val decls: List<DeclSyntax>,
) : PrettySyntax {
    override val span: SourceSpan = if (decls.isEmpty()) SourceSpan(0, 0) else SourceSpan(decls.first().span.start, decls.last().span.end)

    override fun pretty(): String = decls.joinToString("\n\n") { it.pretty() }
}
