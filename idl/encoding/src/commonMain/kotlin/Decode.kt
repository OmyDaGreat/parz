package xyz.malefic.parz.idl.encoding

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Checked
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.core.sink
import xyz.malefic.parz.idl.model.Decl
import xyz.malefic.parz.idl.model.Resolved
import xyz.malefic.parz.idl.model.Schema
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Primitive.Decimal.Rounding.PROHIBITED
import xyz.malefic.parz.idl.model.Value
import xyz.malefic.parz.idl.model.check
import xyz.malefic.parz.idl.model.isExactAt
import xyz.malefic.parz.idl.model.rescale

/**
 * Strictly decodes a given string into a value. Either returns a value or diagnostics.
 */
fun decodeString(
    text: String,
    type: Type,
    schema: Schema = Schema.EMPTY,
) = decodeStringLenient(text, type, schema).let {
    if (it.diagnostics.isEmpty()) it else Checked(null, it.diagnostics)
}

/**
 * Leniently decodes a given string into a value. Returns a value w/ diagnostics when the only problems are recoverable read errors.
 */
fun decodeStringLenient(
    text: String,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Checked<Value?, AnyDiagnostic> =
    collecting {
        val sexps = readSexps(text)
        when (val single = sexps.singleOrNull()) {
            null -> null.also { sink().report(Diagnostic(CodecErrorKind.ExpectedOneValue(sexps.size), null)) }
            else -> decode(single, type, schema)
        }
    }

private val HEX = Regex("([0-9a-fA-F]{2})*")
private val DIGITS = Regex("0|[1-9][0-9]*")

/**
 * Decodes an S-expression into a value.
 */
context(sink: Sink<AnyDiagnostic>)
fun decode(
    sexp: Sexp,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Value? =
    when (type) {
        Type.Primitive.Void -> {
            if (sexp is Sexp.List && sexp.items.isEmpty()) Value.Void else sexp.fail(CodecErrorKind.Expected("()"))
        }

        Type.Primitive.Bool -> {
            when ((sexp as? Sexp.Atom)?.text) {
                "true" -> Value.Bool(true)
                "false" -> Value.Bool(false)
                else -> sexp.fail(CodecErrorKind.Expected("true or false"))
            }
        }

        is Type.Primitive.Id -> {
            val id = (sexp as? Sexp.Atom)?.text?.takeIf { DIGITS.matches(it) } ?: return sexp.fail(CodecErrorKind.Expected("an id"))
            val n: Int
            try {
                n = id.toBigInteger().intValue(true)
            } catch (_: ArithmeticException) {
                return sexp.fail(CodecErrorKind.Expected("an id"))
            }
            fits(Value.Id(n), type, sexp)
        }

        is Type.Primitive.Bytes -> {
            if (sexp !is Sexp.Atom || !sexp.text.startsWith("#x")) {
                sexp.fail(CodecErrorKind.Expected("bytes like #xc1c2"))
            } else {
                val hex =
                    sexp.text.substring(2).takeIf { HEX.matches(it) } ?: return sexp.fail(CodecErrorKind.Expected("bytes like #xc1c2"))
                fits(Value.Bytes(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()), type, sexp)
            }
        }

        is Type.Primitive.Decimal -> {
            val parsed = parseDecimal(sexp)
            if (parsed == null) sexp.fail(CodecErrorKind.Expected("a decimal")) else fits(parsed.rescaleIfExact(type.scale), type, sexp)
        }

        is Type.Primitive.Str -> {
            if (sexp is Sexp.Str) fits(Value.Str(sexp.value), type, sexp) else sexp.fail(CodecErrorKind.Expected("a string"))
        }

        is Type.Collection.Option -> {
            when {
                sexp is Sexp.Atom && sexp.text == "none" -> Value.None
                sexp.isForm("some") && (sexp as Sexp.List).items.size == 2 -> decode(sexp.items[1], type.inner, schema)?.let(Value::Some)
                else -> sexp.fail(CodecErrorKind.Expected("none or (some ...)"))
            }
        }

        is Type.Collection.Seq -> {
            if (sexp is Sexp.List) {
                all(sexp.items.map { decode(it, type.inner, schema) })?.let(Value::Seq)
            } else {
                sexp.fail(CodecErrorKind.Expected("a list"))
            }
        }

        is Type.Collection.Map -> {
            if (sexp is Sexp.List) {
                val map = mutableMapOf<Value, Value>()
                var failed = false
                for (pair in sexp.items) {
                    if (pair !is Sexp.List) {
                        pair.fail(CodecErrorKind.Expected("(key value)"))
                        failed = true
                        continue
                    }
                    if (pair.items.size != 2) {
                        pair.fail(CodecErrorKind.Expected("(key value)"))
                        failed = true
                        continue
                    }
                    val key =
                        decode(pair.items[0], type.keys, schema) ?: run {
                            failed = true
                            continue
                        }
                    if (key in map) {
                        pair.items[0].fail(CodecErrorKind.DuplicateKey(key))
                        failed = true
                        continue
                    }
                    val value =
                        decode(pair.items[1], type.values, schema) ?: run {
                            failed = true
                            continue
                        }
                    map[key] = value
                }
                if (failed) null else Value.Map(map)
            } else {
                sexp.fail(CodecErrorKind.Expected("a list"))
            }
        }

        is Type.Collection.Table -> {
            if (sexp is Sexp.List) {
                all(sexp.items.map { decode(it, type.inner, schema) })?.let(Value::Seq)
            } else {
                sexp.fail(CodecErrorKind.Expected("a list"))
            }
        }

        is Type.Ref -> {
            when (val resolved = schema.resolve(type)) {
                is Resolved.Record -> decodeRecord(sexp, resolved.decl, schema)
                is Resolved.Variant -> decodeVariant(sexp, resolved.decl, schema)
                is Resolved.Other -> decode(sexp, resolved.type, schema)
            }
        }
    }

context(_: Sink<AnyDiagnostic>)
private fun decodeRecord(
    sexp: Sexp,
    decl: Decl.Record,
    schema: Schema,
) = if (sexp is Sexp.List) {
    decodeFields(
        sexp,
        sexp.items,
        decl.fields,
        schema,
    )?.let(Value::Record)
} else {
    sexp.fail(CodecErrorKind.Expected("a record"))
}

context(sink: Sink<AnyDiagnostic>)
private fun decodeVariant(
    sexp: Sexp,
    decl: Decl.Variant,
    schema: Schema,
): Value? {
    val head =
        when (sexp) {
            is Sexp.Atom -> sexp
            is Sexp.List -> sexp.items.firstOrNull() as? Sexp.Atom
            is Sexp.Str -> null
        } ?: return sexp.fail(CodecErrorKind.Expected("a member of ${decl.name}"))
    val member = decl.members.find { it.name == head.text } ?: return head.fail(CodecErrorKind.UnknownMember(head.text))
    return when {
        member.payload.isEmpty() && sexp is Sexp.Atom -> {
            Value.Variant(member.name)
        }

        member.payload.isNotEmpty() && sexp is Sexp.List -> {
            decodeFields(sexp, sexp.items.drop(1), member.payload, schema)?.let {
                Value.Variant(member.name, it)
            }
        }

        member.payload.isEmpty() -> {
            sexp.fail(CodecErrorKind.Expected("'${member.name}' without fields"))
        }

        else -> {
            sexp.fail(CodecErrorKind.Expected("(${member.name} (field value) ...)"))
        }
    }
}

/** Decodes `(name value)` entries in any order. Reports every bad, unknown, duplicate, and missing field before giving up. */
context(sink: Sink<AnyDiagnostic>)
private fun decodeFields(
    whole: Sexp,
    entries: List<Sexp>,
    declared: List<Decl.Record.Field>,
    schema: Schema,
): Value.Fields? {
    val byName = declared.associateBy { it.name }
    val values = mutableMapOf<String, Value>()
    val seen = mutableSetOf<String>()
    var failed = false
    for (entry in entries) {
        val name = entry.fieldName()
        when {
            name == null -> {
                entry.fail(CodecErrorKind.Expected("(field value)"))
                failed = true
            }

            name.text !in byName -> {
                name.fail(CodecErrorKind.UnknownField(name.text))
                failed = true
            }

            !seen.add(name.text) -> {
                name.fail(CodecErrorKind.DuplicateField(name.text))
                failed = true
            }

            else -> {
                decode((entry as Sexp.List).items[1], byName.getValue(name.text).type, schema)?.let { values[name.text] = it }
                    ?: run { failed = true }
            }
        }
    }
    declared.filter { it.name !in seen }.forEach {
        whole.fail(CodecErrorKind.MissingField(it.name))
        failed = true
    }
    return if (failed) null else values
}

/** Checks range, length, and scale on a decoded leaf, so the error points at that leaf. */
context(sink: Sink<Diagnostic<CodecErrorKind>>)
private fun fits(
    value: Value,
    type: Type,
    sexp: Sexp,
): Value? =
    when (val mismatch = type.check(value, Schema.EMPTY).firstOrNull()) {
        null -> value
        else -> sexp.fail(CodecErrorKind.DoesNotFit(mismatch.reason))
    }

private val DECIMAL = Regex("-?[0-9]+(\\.[0-9]+)?")

private fun parseDecimal(sexp: Sexp): Value.Decimal? {
    val text = (sexp as? Sexp.Atom)?.text?.takeIf { DECIMAL.matches(it) } ?: return null
    return Value.Decimal(BigInteger.parseString(text.replace(".", ""), 10), text.substringAfter('.', "").length)
}

private fun Value.Decimal.rescaleIfExact(scale: Int) = if (isExactAt(scale)) rescale(scale, PROHIBITED) else this

private fun Sexp.isForm(head: String) = this is Sexp.List && (items.firstOrNull() as? Sexp.Atom)?.text == head

private fun Sexp.fieldName() = (this as? Sexp.List)?.takeIf { it.items.size == 2 }?.items?.first() as? Sexp.Atom

private fun <T : Any> all(items: List<T?>): List<T>? = if (null in items) null else items.requireNoNulls()

@IgnorableReturnValue
context(sink: Sink<Diagnostic<CodecErrorKind>>)
private fun Sexp.fail(kind: CodecErrorKind): Value? = null.also { sink.report(Diagnostic(kind, span)) }
