package xyz.malefic.parz.idl.model

import xyz.malefic.parz.idl.model.Decl.Alias
import xyz.malefic.parz.idl.model.Decl.Record
import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyName
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyRecord
import xyz.malefic.parz.idl.model.ShapeErrorKind.EmptyVariant
import xyz.malefic.parz.idl.model.ShapeErrorKind.NegativeTag

sealed interface Decl {
    val name: String

    data class Record(
        override val name: String,
        val fields: List<Field>,
    ) : Decl {
        init {
            requireShape(EmptyName.check(name) + EmptyRecord.check(fields))
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

    data class Variant(
        override val name: String,
        val members: List<Member>,
        val isEnum: Boolean = false,
    ) : Decl {
        init {
            requireShape(
                EmptyName.check(name) + EmptyVariant.check(members) +
                    if (isEnum) members.flatMap { ShapeErrorKind.EnumPayload.check(it) } else emptyList(),
            )
        }

        data class Member(
            val name: String,
            val tag: Int,
            val payload: List<Field> = emptyList(),
        ) {
            init {
                requireShape(EmptyName.check(name) + NegativeTag.check(tag))
            }
        }
    }

    data class Alias(
        override val name: String,
        val type: Type,
    ) : Decl {
        init {
            requireShape(EmptyName.check(name))
        }
    }
}

sealed interface Resolved {
    data class Record(
        val decl: Decl.Record,
    ) : Resolved

    data class Variant(
        val decl: Decl.Variant,
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
                    is Record -> Resolved.Record(decl)
                    is Variant -> Resolved.Variant(decl)
                    is Alias -> resolve(decl.type)
                }
            }

            else -> {
                Resolved.Other(type)
            }
        }

    fun validate() = validate(decls)
}
