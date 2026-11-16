package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.Type

/**
 * A representation of an S Expression file
 */
data class ParzFile(
    val root: Type.Ref,
    val rootSpan: SourceSpan?,
    val body: List<Sexp>,
)
