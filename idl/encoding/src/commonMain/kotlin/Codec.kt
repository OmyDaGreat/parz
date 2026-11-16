package xyz.malefic.parz.idl.encoding

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Checked
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.Sink
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.core.sink
import xyz.malefic.parz.idl.Decl
import xyz.malefic.parz.idl.Resolved
import xyz.malefic.parz.idl.Schema
import xyz.malefic.parz.idl.Type
import xyz.malefic.parz.idl.Value
import xyz.malefic.parz.idl.check

const val FORMAT_VERSION = 1

fun encodeToString(
    value: Value,
    type: Type,
    schema: Schema = Schema.EMPTY,
): String = encode(value, type, schema).print()

fun encodeFile(
    value: Value,
    root: Type.Ref,
    schema: Schema,
    id: SchemaId,
    targetWidth: Int = 80,
): String {
    val header =
        Sexp.List(
            listOf(
                Sexp.Atom("parz"),
                Sexp.Atom("$FORMAT_VERSION"),
                id.toSexp(),
                Sexp.Atom(root.name),
            ),
        )
    val body = encode(value, root, schema)
    return "${header.print()}\n${body.pretty(targetWidth)}\n"
}

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
        is Type.Void -> Sexp.List(emptyList())
        is Type.Bool -> Sexp.Atom((value as Value.Bool).v.toString())
        is Type.Decimal -> Sexp.Atom(decimalText(value as Value.Decimal, type.scale))
        is Type.Str -> Sexp.Str((value as Value.Str).v)
        is Type.Option -> option(value, type.inner, schema)
        is Type.Seq -> Sexp.List((value as Value.Seq).items.map { encodeChecked(it, type.inner, schema) })
        is Type.Ref -> encodeRef(value, type, schema)
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

fun decodeString(
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

fun decodeFile(
    text: String,
    schema: Schema,
    id: SchemaId,
    expected: Type.Ref? = null,
) = collecting {
    fun reportNull(e: CodecErrorKind) = sink().report(Diagnostic(e, null)).let { null }

    val (file, errors) = readFile(text, schema, id)
    errors.forEach(sink()::report)
    if (file == null) return@collecting null

    if (expected != null && file.root != expected) {
        return@collecting reportNull(CodecErrorKind.WrongRootType(expected?.name ?: "<empty>", file.root.name))
    }
    if (file.body.size != 1) {
        return@collecting reportNull(CodecErrorKind.ExpectedOneValue(file.body.size))
    }
    decode(file.body.single(), file.root, schema)
}

fun readFile(
    text: String,
    schema: Schema,
    id: SchemaId,
): Checked<ParzFile?, AnyDiagnostic> =
    collecting {
        fun fail(kind: CodecErrorKind) = null.also { sink().report(Diagnostic(kind, null)) }

        val sexps = readSexps(text)
        val parts = (sexps.firstOrNull() as? Sexp.List)?.items
        if ((parts?.firstOrNull() as? Sexp.Atom)?.text != "parz") {
            return@collecting fail(CodecErrorKind.MissingHeader)
        }
        if ((parts.getOrNull(1) as? Sexp.Atom)?.text != FORMAT_VERSION.toString()) {
            return@collecting fail(CodecErrorKind.Expected("(parz <version> (schema <name> <version>) <Root>)"))
        }
        val givenSchema = parts.getOrNull(2) as? Sexp.List
        if (givenSchema?.items?.size != 3) {
            return@collecting fail(CodecErrorKind.SchemaMismatch(id, givenSchema?.print() ?: "<empty>"))
        }
        if ((givenSchema.items.getOrNull(0) as? Sexp.Atom)?.text != "schema") {
            return@collecting fail(CodecErrorKind.SchemaMismatch(id, givenSchema.print()))
        }
        if ((givenSchema.items.getOrNull(1) as? Sexp.Atom)?.text != id.name) {
            return@collecting fail(CodecErrorKind.SchemaMismatch(id, givenSchema.print()))
        }
        if ((givenSchema.items.getOrNull(2) as? Sexp.Atom)?.text != "${id.version}") {
            return@collecting fail(CodecErrorKind.SchemaMismatch(id, givenSchema.print()))
        }
        val root = parts.getOrNull(3) as? Sexp.Atom
        if (root?.text !in schema.decls.map { it.name }) {
            return@collecting fail(CodecErrorKind.UnknownRootType(root?.print() ?: "<empty>"))
        }
        if ((parts.getOrNull(4)) != null) {
            return@collecting fail(CodecErrorKind.Expected("(parz <version> (schema <name> <version>) <Root>)"))
        }
        ParzFile(Type.Ref(root!!.text), sexps.drop(1))
    }

context(sink: Sink<AnyDiagnostic>)
fun decode(
    sexp: Sexp,
    type: Type,
    schema: Schema = Schema.EMPTY,
): Value? =
    when (type) {
        Type.Void -> {
            if (sexp is Sexp.List && sexp.items.isEmpty()) Value.Void else sexp.fail(CodecErrorKind.Expected("()"))
        }

        Type.Bool -> {
            when ((sexp as? Sexp.Atom)?.text) {
                "true" -> Value.Bool(true)
                "false" -> Value.Bool(false)
                else -> sexp.fail(CodecErrorKind.Expected("true or false"))
            }
        }

        is Type.Decimal -> {
            val parsed = parseDecimal(sexp)
            if (parsed == null) sexp.fail(CodecErrorKind.Expected("a decimal")) else fits(parsed.rescaleIfExact(type.scale), type, sexp)
        }

        is Type.Str -> {
            if (sexp is Sexp.Str) fits(Value.Str(sexp.value), type, sexp) else sexp.fail(CodecErrorKind.Expected("a string"))
        }

        is Type.Option -> {
            when {
                sexp is Sexp.Atom && sexp.text == "none" -> Value.None
                sexp.isForm("some") && (sexp as Sexp.List).items.size == 2 -> decode(sexp.items[1], type.inner, schema)?.let(Value::Some)
                else -> sexp.fail(CodecErrorKind.Expected("none or (some ...)"))
            }
        }

        is Type.Seq -> {
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

/** Decodes `(name value)` entries in any order. Reports every bad, unknown, duplicate, and msising field before giving up. */
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

private fun Value.Decimal.rescaleIfExact(scale: Int) = if (isExactAt(scale)) rescale(scale, Type.Decimal.Rounding.PROHIBITED) else this

private fun Sexp.isForm(head: String) = this is Sexp.List && (items.firstOrNull() as? Sexp.Atom)?.text == head

private fun Sexp.fieldName() = (this as? Sexp.List)?.takeIf { it.items.size == 2 }?.items?.first() as? Sexp.Atom

private fun <T : Any> all(items: List<T?>): List<T>? = if (null in items) null else items.requireNoNulls()

@IgnorableReturnValue
context(sink: Sink<Diagnostic<CodecErrorKind>>)
private fun Sexp.fail(kind: CodecErrorKind): Value? = null.also { sink.report(Diagnostic(kind, span)) }
