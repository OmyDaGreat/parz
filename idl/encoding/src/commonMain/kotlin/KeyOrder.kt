package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.idl.Resolved
import xyz.malefic.parz.idl.Schema
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Type.Primitive.Decimal.Rounding.PROHIBITED
import xyz.malefic.parz.idl.Value

/**
 * The canonical order of map keys of type [type], which both encoders (Kotlin and OCaml) must follow exactly:
 *
 * - `bool`: `false` before `true`
 * - `decimal`: by numeric value
 * - `string`: by Unicode code point (not UTF-16 code unit)
 * - variant without payloads (enum): by member tag, so reordering members in the IDL doesn't reorder files
 * - alias: the order of its target
 *
 * Keys must already conform to [type]; the schema's map key check guarantees no other key types reach here.
 */
internal fun keyOrder(
    type: Type,
    schema: Schema,
): Comparator<Value> =
    when (type) {
        Type.Primitive.Bool -> {
            compareBy { (it as Value.Bool).v }
        }

        is Type.Primitive.Decimal -> {
            compareBy { (it as Value.Decimal).rescale(type.scale, PROHIBITED).unscaled }
        }

        is Type.Primitive.Str -> {
            Comparator { a, b -> compareCodePoints((a as Value.Str).v, (b as Value.Str).v) }
        }

        is Type.Ref -> {
            when (val resolved = schema.resolve(type)) {
                is Resolved.Variant -> {
                    if (!resolved.decl.isEnum) {
                        error("Only enums can be map keys: ${type.name}")
                    }
                    val tags = resolved.decl.members.associate { it.name to it.tag }
                    compareBy { tags[(it as Value.Variant).name] }
                }

                is Resolved.Other -> {
                    keyOrder(resolved.type, schema)
                }

                is Resolved.Record -> {
                    error("Records can't be map keys: ${type.name}")
                }
            }
        }

        else -> {
            error("Not a valid map key type: $type")
        }
    }

/**
 * Compares strings by code point. UTF-16 order only disagrees with code point order when a surrogate (U+D800–U+DFFF, half of a
 * character above U+FFFF) meets a character in U+E000–U+FFFF, so those two ranges are swapped before comparing.
 */
private fun compareCodePoints(
    a: String,
    b: String,
): Int {
    for (i in 0 until minOf(a.length, b.length)) {
        if (a[i] != b[i]) return a[i].codePointOrder() - b[i].codePointOrder()
    }
    return a.length - b.length
}

private fun Char.codePointOrder() =
    when {
        this >= '' -> code - 0x800
        this >= '\uD800' -> code + 0x2000
        else -> code
    }
