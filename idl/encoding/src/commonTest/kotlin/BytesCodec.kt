package xyz.malefic.parz.idl.encoding

import xyz.malefic.parz.idl.encoding.CodecErrorKind.DoesNotFit
import xyz.malefic.parz.idl.model.Reason.Fit.InexactLength
import xyz.malefic.parz.idl.model.Reason.Fit.TooLong
import xyz.malefic.parz.idl.model.Reason.Fit.TooShort
import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Collection.Map
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Bytes
import xyz.malefic.parz.idl.model.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `bytes` as `#x` followed by two lowercase hex digits per byte.
 */
class BytesCodec {
    private fun bytes(vararg values: Int) = Value.Bytes(values.map { it.toByte() }.toByteArray())

    private val any = Bytes(0L..Long.MAX_VALUE)

    private val yes = Value.Bool(true)

    /** The single error decoding [text] as [type], with its span covering [text]. */
    private fun error(
        text: String,
        type: Type,
    ): CodecErrorKind {
        val diagnostics = decodeErrors(text, type)
        assertEquals(1, diagnostics.size, "expected one error decoding $text, got $diagnostics")
        assertEquals(text.span(text), diagnostics.single().span)
        return diagnostics.single().kind as CodecErrorKind
    }

    @Test
    fun encoding() {
        assertEquals("#x", encoded(bytes(), any))
        assertEquals("#x00", encoded(bytes(0), any))
        assertEquals("#xc1c2", encoded(bytes(0xc1, 0xc2), any))
        assertEquals("#x00017f80ff", encoded(bytes(0, 1, 0x7f, 0x80, 0xff), any))
    }

    @Test
    fun decoding() {
        assertEquals(bytes(), decodeOk("#x", any))
        assertEquals(bytes(0xc1, 0xc2), decodeOk("#xc1c2", any))
        assertEquals(bytes(0, 0xff), decodeOk("#x00ff", any))
    }

    @Test
    fun uppercaseIsAcceptedAndNormalised() {
        assertEquals(bytes(0xab, 0xcd), decodeOk("#xABcD", any))
        assertEquals("#xabcd", encoded(decodeOk("#xABCD", any), any))
    }

    @Test
    fun malformed() {
        listOf("#xc1c", "#xzz", "#x-1", "#x+1", "#xg0", "#X00", "c1c2", "0xc1", "#c1", "x00").forEach { text ->
            assertEquals(CodecErrorKind.Expected("bytes like #xc1c2"), error(text, any), text)
        }
    }

    @Test
    fun wrongForms() {
        assertEquals(CodecErrorKind.Expected("bytes like #xc1c2"), error("\"#xc1\"", any))
        assertEquals(CodecErrorKind.Expected("bytes like #xc1c2"), error("(#xc1)", any))
        assertEquals(CodecErrorKind.Expected("bytes like #xc1c2"), error("()", any))
    }

    @Test
    fun exactLength() {
        assertEquals(bytes(1, 2), decodeOk("#x0102", Bytes(2L)))
        assertEquals(DoesNotFit(InexactLength(2, 1)), error("#x01", Bytes(2L)))
        assertEquals(DoesNotFit(InexactLength(2, 3)), error("#x010203", Bytes(2L)))
        assertEquals("Expected a length of 2, got 1", error("#x01", Bytes(2L)).message)
    }

    @Test
    fun lengthRange() {
        val type = Bytes(1L..3L)
        assertEquals(DoesNotFit(TooShort(1, 0)), error("#x", type))
        assertEquals(DoesNotFit(TooLong(3, 4)), error("#x01020304", type))
        assertEquals("Too short (0 < 1)", error("#x", type).message)
    }

    @Test
    fun errorsPointIntoRecords() {
        val text = "(#x01 #xzz #x0102030405)"
        val diagnostics = decodeErrors(text, Type.Collection.Seq(Bytes(1L..4L)))
        assertEquals(listOf("IDL210", "IDL215"), codes(diagnostics))
        assertEquals(listOf(text.span("#xzz"), text.span("#x0102030405")), diagnostics.map { it.span })
    }

    @Test
    fun roundTrips() {
        listOf(bytes(), bytes(0), bytes(0xff), bytes(*(0..255).toList().toIntArray())).forEach {
            assertEquals(it, decodeOk(encoded(it, any), any))
            assertEquals(encoded(it, any), encoded(decodeOk(encoded(it, any), any), any))
        }
    }

    @Test
    fun encodingRejectsWrongLengths() {
        assertFailsWith<IllegalArgumentException> { encode(bytes(1), Bytes(2L)) }
        assertFailsWith<IllegalArgumentException> { encode(bytes(), Bytes(1L..2L)) }
    }

    @Test
    fun mapKeysByUnsignedBytesWithPrefixesFirst() {
        val keys = listOf(bytes(0xff), bytes(0x80), bytes(0x7f, 0), bytes(0x7f), bytes(0, 0), bytes(), bytes(1, 0, 2), bytes(1, 0, 1))
        val map = Value.Map(keys.associateWith { yes })
        assertEquals(
            "((#x true) (#x0000 true) (#x010001 true) (#x010002 true) (#x7f true) (#x7f00 true) (#x80 true) (#xff true))",
            encoded(map, Map(any, Bool)),
        )
    }

    @Test
    fun mapKeysOrderMatchesTheHexText() {
        val keys = (0..40).map { n -> bytes(*IntArray(n % 3 + 1) { (n * 37 + it * 101) % 256 }) }
        val map = Value.Map(keys.associateWith { yes })
        val printedKeys = (encode(map, Map(any, Bool)) as Sexp.List).items.map { ((it as Sexp.List).items[0] as Sexp.Atom).text }
        assertEquals(printedKeys.sorted(), printedKeys)
    }

    @Test
    fun duplicateMapKeysAreRejectedWhenDecoding() =
        assertEquals(listOf("IDL222"), codes(decodeErrors("((#x01 true) (#x01 false))", Map(any, Bool))))
}
