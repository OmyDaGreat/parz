package xyz.malefic.parz.idl.encoding

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Checked
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Resolved
import xyz.malefic.parz.idl.Schema
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.check

fun encode(
    value: Value,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Sexp {
    val mismatches = type.check(value, schema)
    require(mismatches.isEmpty()) { "Value does not conform to $type: $mismatches" }
    return encodeChecked(value, type, schema)
}

fun encodeChecked(
    value: Value,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Sexp =
    when (type) {
        is Type.Void -> Sexp.List(emptyList())
        is Type.Bool -> Sexp.Atom((value as Value.Bool).v.toString())
        is Type.Decimal -> Sexp.Atom(decimalText(value as Value.Decimal, type.scale))
        is Type.Str -> Sexp.Str((value as Value.Str).v)
        is Type.Option -> option(value, type.inner, schema)
        is Type.Seq -> Sexp.List((value as Value.Seq).items.map { encodeChecked(it, type.inner, schema) })
        is Type.Ref -> encodeRef(type, value, schema)
    }

private fun decimalText(
    value: Value.Decimal,
    scale: Int,
): String {
    val unscaled = value.rescale(scale, Type.Decimal.Rounding.PROHIBITED).unscaled
    if (scale <= 0) return (unscaled * BigInteger.TEN.pow(-scale)).toString()
    val digits = unscaled.abs().toString().padStart(scale + 1, '0')
    val sign = if (unscaled.signum() < 0) "-" else ""
    return sign + digits.dropLast(scale) + "." + digits.takeLast(scale)
}

private fun encodeRef(
    type: Type.Ref,
    value: Value,
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

context(sink: Sink<AnyDiagnostic>)
fun decode(
    sexp: Sexp,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Value? = TODO()

fun encodeToString(
    value: Value,
    type: Type,
    schema: Schema = Schema.EMPTY,
): String = encode(value, type, schema).print()

fun decodeString(
    text: String,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Checked<Value?, AnyDiagnostic> = TODO()
