package xyz.malefic.parz.idl.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.DiagnosticKind
import xyz.malefic.parz.core.Severity
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant.Member

sealed interface SchemaIssueKind : DiagnosticKind {
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
    ) : SchemaIssueKind {
        override val code = "IDL101"
        override val message = "Unknown type${if (path != null) " in field $path" else ""}: $name"
    }

    data class DuplicateFieldName(
        val path: FieldPath,
        override val decl: Decl,
    ) : SchemaIssueKind {
        override val code = "IDL102"
        override val message = "Duplicate field ${path.field.name} in ${decl.name}"
    }

    sealed interface MemberIssue : SchemaIssueKind {
        val member: Member

        data class DuplicateMemberName(
            val name: String,
            override val decl: Decl,
            override val member: Member,
        ) : MemberIssue {
            override val code = "IDL103"
            override val message = "Duplicate member name: $name"
        }

        data class DuplicateMemberTag(
            val tag: Int,
            override val decl: Decl,
            override val member: Member,
        ) : MemberIssue {
            override val code = "IDL104"
            override val message = "Duplicate member tag: $tag"
        }
    }

    data class CyclicType(
        val path: List<String>,
        override val decl: Decl,
    ) : SchemaIssueKind {
        override val code = "IDL105"
        override val message = "Cyclic type definition: ${path.joinToString(" -> ")}"
    }

    data class DuplicateDecl(
        override val decl: Decl,
    ) : SchemaIssueKind {
        override val code = "IDL106"
        override val message = "Duplicate declaration: ${decl.name}"
    }

    /**
     * A map's key type isn't a bool, string, id, bytes, decimal, or variant without payloads (an enum), directly or through aliases.
     */
    data class InvalidMapKey(
        val key: Type,
        override val decl: Decl,
        val path: FieldPath?,
    ) : SchemaIssueKind {
        override val code = "IDL107"
        override val message =
            "Invalid map key${if (path != null) " in field $path" else ""}: ${(key as? Type.Ref)?.name ?: key.kind}" +
                " (keys must be bool, string, id, bytes, decimal, or an enum)"
    }

    /**
     * An `id<[name]>` is used, but no `table<[name]>` appears anywhere in the schema, so no value could ever hold its entries.
     */
    data class MissingTable(
        val name: String,
        override val decl: Decl,
        val path: FieldPath?,
    ) : SchemaIssueKind {
        override val code = "IDL108"
        override val message = "No table<$name> for id<$name>${if (path != null) " in field $path" else ""}"
        override val severity = Severity.WARNING
    }

    /**
     * An `id<…>` or `table<…>` ([type]) doesn't name a record or variant directly. Ids index tables of declarations, so aliases
     * and unnamed types can't be targets.
     */
    data class InvalidTarget(
        val type: Type,
        override val decl: Decl,
        val path: FieldPath?,
    ) : SchemaIssueKind {
        override val code = "IDL109"
        override val message =
            "${if (type is Type.Primitive.Id) "Id" else "Table"} target must be a record or variant" +
                "${if (path != null) " in field $path" else ""}: ${
                    when (type) {
                        is Type.Primitive.Id -> type.target
                        is Type.Collection.Table -> (type.inner as? Type.Ref)?.name ?: type.inner.kind
                        else -> type.kind
                    }
                }"
    }
}

sealed interface ShapeErrorKind : DiagnosticKind {
    data object EmptyRecord : ShapeErrorKind {
        override val code = "IDL120"
        override val message = "Record must have at least one field"

        fun check(fields: List<*>) = listOfNotNull(EmptyRecord.takeIf { fields.isEmpty() })
    }

    data object EmptyVariant : ShapeErrorKind {
        override val code = "IDL121"
        override val message = "Variant must have at least one member"

        fun check(members: List<*>) = listOfNotNull(EmptyVariant.takeIf { members.isEmpty() })
    }

    data object EmptyName : ShapeErrorKind {
        override val code = "IDL122"
        override val message = "Name must not be empty"

        fun check(name: String) = listOfNotNull(EmptyName.takeIf { name.isBlank() })
    }

    data class NegativeTag(
        val tag: Int,
    ) : ShapeErrorKind {
        override val code = "IDL123"
        override val message = "Variant member tag $tag must be non-negative"

        companion object {
            fun check(tag: Int) = listOfNotNull(NegativeTag(tag).takeIf { tag < 0 })
        }
    }

    data class EmptyRange(
        val min: BigInteger,
        val max: BigInteger,
    ) : ShapeErrorKind {
        override val code = "IDL124"
        override val message = "Range must not be empty or negative: [$min, $max]"

        companion object {
            fun check(
                min: BigInteger,
                max: BigInteger,
            ) = listOfNotNull(EmptyRange(min, max).takeIf { min > max })

            fun check(
                min: Long,
                max: Long,
            ) = listOfNotNull(EmptyRange(min.toBigInteger(), max.toBigInteger()).takeIf { min > max })
        }
    }

    data class NonPositiveLength(
        val length: Long,
    ) : ShapeErrorKind {
        override val code = "IDL125"
        override val message = "Length $length must be positive"

        companion object {
            fun check(maxLen: Long) = listOfNotNull(NonPositiveLength(maxLen).takeIf { maxLen <= 0 })
        }
    }

    data class EnumPayload(
        val member: Member,
    ) : ShapeErrorKind {
        override val code = "IDL126"
        override val message = "Payload of variant member '${member.name}' must be empty when declared as enum"

        companion object {
            fun check(member: Member) = listOfNotNull(EnumPayload(member).takeIf { member.payload.isNotEmpty() })
        }
    }

    data class NegativeLength(
        val length: Long,
    ) : ShapeErrorKind {
        override val code = "IDL127"
        override val message = "Length $length must be non-negative"

        companion object {
            fun check(length: Long) = listOfNotNull(NegativeLength(length).takeIf { length < 0 })
        }
    }

    companion object {
        /**
         * Reports a list of shape errors.
         * @param span The span to report the errors at.
         * @return `true` if the list is empty, `false` otherwise.
         */
        context(sink: Sink<AnyDiagnostic>)
        fun List<ShapeErrorKind>.reportAt(span: SourceSpan?) = isEmpty().also { forEach { sink.report(Diagnostic(it, span)) } }
    }
}

fun requireShape(errors: List<ShapeErrorKind>) =
    require(errors.isEmpty()) {
        "Shape validation failed: ${errors.joinToString { it.message }}"
    }
