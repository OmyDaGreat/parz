package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.AnyDiagnostic
import xyz.malefic.parz.core.Checked
import xyz.malefic.parz.core.Diagnostic
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.core.sink
import xyz.malefic.parz.idl.model.Schema
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Value
import xyz.malefic.parz.idl.model.checkReferences

const val FORMAT_VERSION = 1

/**
 * A representation of an S Expression file
 */
data class ParzFile(
    val root: Type.Ref,
    val rootSpan: SourceSpan?,
    val body: List<Sexp>,
)

data class SchemaId(
    val name: String,
    val version: Int,
) {
    init {
        require(name.isNotEmpty() && name.none { it.isDelimiter() }) { "Invalid schema name: '$name'" }
        require(version >= 0) { "Invalid schema version: '$version'" }
    }

    fun toSexp() = Sexp.List(listOf(Sexp.Atom("schema"), Sexp.Atom(name), Sexp.Atom("$version")))

    override fun toString() = "$name $version"
}

fun encodeFile(
    value: Value,
    root: Type.Ref,
    schema: Schema,
    id: SchemaId,
    targetWidth: Int = 80,
): String {
    val header = header(id, root)
    val body = encode(value, root, schema)
    val references = checkReferences(value, root, schema)
    require(references.isEmpty()) { "Value has bad references: $references" }
    return "${header.print()}\n${body.pretty(targetWidth)}\n"
}

/** The first form of every file: `(parz <version> (schema <name> <version>) <Root>)`. */
private fun header(
    id: SchemaId,
    root: Type.Ref,
) = Sexp.List(listOf(Sexp.Atom("parz"), Sexp.Atom("$FORMAT_VERSION"), id.toSexp(), Sexp.Atom(root.name)))

/**
 * Strictly decodes a given file (string w/ header and body) into a value. Either returns a value or diagnostics.
 */
fun decodeFile(
    text: String,
    schema: Schema,
    id: SchemaId,
    expected: Type.Ref? = null,
) = decodeFileLenient(text, schema, id, expected).let {
    if (it.diagnostics.isEmpty()) it else Checked(null, it.diagnostics)
}

/**
 * Leniently decodes a given file (string w/ header and body) into a value. Returns a value w/ diagnostics when the only problems are recoverable read errors.
 */
fun decodeFileLenient(
    text: String,
    schema: Schema,
    id: SchemaId,
    expected: Type.Ref? = null,
) = collecting {
    val (file, errors) = readFile(text, schema, id)
    errors.forEach(sink()::report)
    if (file == null) return@collecting null

    fun reportNull(
        e: CodecErrorKind,
        span: SourceSpan? = file.rootSpan,
    ) = sink().report(Diagnostic(e, span)).let { null }

    if (expected != null && file.root != expected) {
        return@collecting reportNull(CodecErrorKind.WrongRootType(expected.name, file.root.name))
    }
    if (file.body.size != 1) {
        return@collecting reportNull(CodecErrorKind.ExpectedOneValue(file.body.size), null)
    }
    decode(file.body.single(), file.root, schema)?.also { value ->
        val bad = checkReferences(value, file.root, schema)
        bad.forEach { reportNull(CodecErrorKind.BadReference(it)) }
    }
}

fun readFile(
    text: String,
    schema: Schema,
    id: SchemaId,
): Checked<ParzFile?, AnyDiagnostic> =
    collecting {
        fun fail(
            kind: CodecErrorKind,
            span: SourceSpan?,
        ) = null.also { sink().report(Diagnostic(kind, span)) }

        val sexps = readSexps(text)
        val parts = (sexps.firstOrNull() as? Sexp.List)?.items
        val header = parts?.firstOrNull() as? Sexp.Atom
        if (header?.text != "parz") {
            return@collecting fail(CodecErrorKind.MissingHeader, sexps.firstOrNull()?.span)
        }

        fun malformed(span: SourceSpan? = null) =
            fail(
                CodecErrorKind.Expected("(parz <version> (schema <name> <version>) <Root>)"),
                span ?: sexps.first().span,
            )

        val versionPart = parts.getOrNull(1) ?: return@collecting malformed()
        val version = versionPart as? Sexp.Atom ?: return@collecting malformed(parts[1].span)
        if (version.text != FORMAT_VERSION.toString()) return@collecting fail(CodecErrorKind.UnsupportedVersion(version.text), version.span)

        val givenSchemaPart = parts.getOrNull(2) ?: return@collecting malformed()
        val givenSchema = givenSchemaPart as? Sexp.List ?: return@collecting malformed(givenSchemaPart.span)
        if (givenSchema.items.map { (it as? Sexp.Atom)?.text } != listOf("schema", id.name, "${id.version}")) {
            return@collecting fail(CodecErrorKind.SchemaMismatch(id, givenSchema.print()), givenSchema.span)
        }

        val rootPart = parts.getOrNull(3) ?: return@collecting malformed()
        val root = rootPart as? Sexp.Atom ?: return@collecting malformed(rootPart.span)
        if (root.text !in schema.decls.map { it.name }) {
            return@collecting fail(CodecErrorKind.UnknownRootType(root.text), root.span)
        }

        if ((parts.getOrNull(4)) != null) return@collecting malformed(parts[4].span)

        ParzFile(Type.Ref(root.text), root.span, sexps.drop(1))
    }
