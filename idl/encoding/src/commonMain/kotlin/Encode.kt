package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.model.Resolved
import xyz.malefic.parz.idl.model.Schema
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Value
import xyz.malefic.parz.idl.model.check
import xyz.malefic.parz.idl.model.text

fun encodeToString(
    value: Value,
    type: Type,
    schema: Schema = Schema.EMPTY,
): String = encode(value, type, schema).print()

fun encode(
    value: Value,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Sexp {
    val mismatches = type.check(value, schema)
    require(mismatches.isEmpty()) { "Value does not conform to $type: $mismatches" }
    return encodeChecked(value, type, schema)
}

private fun encodeChecked(
    value: Value,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Sexp =
    when (type) {
        is Type.Primitive.Void -> {
            Sexp.List(emptyList())
        }

        is Type.Primitive.Bool -> {
            Sexp.Atom((value as Value.Bool).v.toString())
        }

        is Type.Primitive.Decimal -> {
            Sexp.Atom((value as Value.Decimal).text(type.scale))
        }

        is Type.Primitive.Bytes -> {
            Sexp.Atom("#x${(value as Value.Bytes).hex()}")
        }

        is Type.Primitive.Str -> {
            Sexp.Str((value as Value.Str).v)
        }

        is Type.Collection.Option -> {
            option(value, type.inner, schema)
        }

        is Type.Collection.Seq -> {
            Sexp.List((value as Value.Seq).items.map { encodeChecked(it, type.inner, schema) })
        }

        is Type.Collection.Map -> {
            val order = keyOrder(type.keys, schema)
            val entries = (value as Value.Map).entries.entries.sortedWith(compareBy(order) { it.key })
            entries.zipWithNext().firstOrNull { (a, b) -> order.compare(a.key, b.key) == 0 }?.let { (a, b) ->
                throw IllegalArgumentException("Duplicate map key: ${a.key} and ${b.key} are the same ${type.keys.kind}")
            }
            Sexp.List(
                entries.map { (k, v) ->
                    Sexp.List(listOf(encodeChecked(k, type.keys, schema), encodeChecked(v, type.values, schema)))
                },
            )
        }

        is Type.Ref -> {
            encodeRef(value, type, schema)
        }
    }

private fun encodeRef(
    value: Value,
    type: Type.Ref,
    schema: Schema,
): Sexp =
    when (val resolved = schema.resolve(type)) {
        is Resolved.Record -> {
            Sexp.List(fields(resolved.decl.fields, (value as Value.Record).fields, schema))
        }

        is Resolved.Variant -> {
            val variant = value as Value.Variant
            val member = resolved.decl.members.find { it.name == variant.name }!!
            if (member.payload.isEmpty()) {
                Sexp.Atom(member.name)
            } else {
                Sexp.List(listOf(Sexp.Atom(member.name)) + fields(member.payload, variant.payload, schema))
            }
        }

        is Resolved.Other -> {
            encodeChecked(value, resolved.type, schema)
        }
    }

private fun fields(
    declared: List<Decl.Record.Field>,
    values: Value.Fields,
    schema: Schema,
) = declared.map { Sexp.List(listOf(Sexp.Atom(it.name), encodeChecked(values.getValue(it.name), it.type, schema))) }

private fun option(
    value: Value,
    innerType: Type,
    schema: Schema,
): Sexp = if (value is Value.Some) Sexp.List(listOf(Sexp.Atom("some"), encodeChecked(value.v, innerType, schema))) else Sexp.Atom("none")
