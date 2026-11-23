package xyz.malefic.parz.idl.syntax

import xyz.malefic.parz.idl.model.Type
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Collection.Table
import xyz.malefic.parz.idl.model.Type.Primitive.Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parsing, printing, and locating errors for `id<T>` and `table<T>`.
 */
class Ids {
    @Test
    fun parses() {
        assertEquals(Id("Para"), typeOf("id<Para>").toType())
        assertEquals(Table(Type.Ref("Para")), typeOf("table<Para>").toType())
        assertEquals(Seq(Id("Para")), typeOf("seq<id<Para>>").toType())
    }

    @Test
    fun spans() {
        listOf("id<Para>", "table<Para>").forEach { text ->
            val source = "type T = $text"
            assertEquals(source.span(text), decl<DeclSyntax.Alias>(source).type.span, text)
        }
    }

    @Test
    fun prints() {
        assertEquals("id<Para>", typeOf("id<Para>").pretty())
        assertEquals("table<Para>", typeOf("table<Para>").pretty())
        assertEquals("map<id<Para>, table<Para>>", typeOf("map<id<Para>, table<Para>>").pretty())
    }

    @Test
    fun roundTrips() =
        listOf("id<Para>", "table<Para>", "option<id<P>>", "map<id<P>, bool>").forEach { text ->
            assertEquals(typeOf(text).toType(), typeOf(typeOf(text).pretty()).toType(), text)
        }

    @Test
    fun idTargetMustBeAName() {
        listOf("id<seq<P>>", "id<>", "id<P, Q>", "id").forEach { text ->
            assertTrue(errors("type T = $text").isNotEmpty(), "$text should not parse")
        }
    }

    @Test
    fun validIdsAndTables() = assertEquals(emptyList(), located("record P { x: bool } record Prog { ps: table<P>, e: id<P> }"))

    @Test
    fun missingTableIsLocatedAtTheId() {
        val source = "record P { x: bool } variant S { go(t: option<id<P>>) }"
        assertEquals(listOf("IDL108" to source.span("id<P>")), located(source))
    }

    @Test
    fun missingTableInMapKey() {
        val source = "record P { x: bool } type T = map<id<P>, bool>"
        assertEquals(listOf("IDL108" to source.span("id<P>")), located(source))
    }

    @Test
    fun invalidTargetsAreLocatedAtTheType() {
        val source = "record P { x: bool } type Q = P record A { t: table<Q>, e: id<Q> }"
        assertEquals(listOf("IDL109" to source.span("table<Q>"), "IDL109" to source.span("id<Q>")), located(source))
    }

    @Test
    fun nestedTableOfUnnamedType() {
        val source = "record A { t: seq<table<bool>> }"
        assertEquals(listOf("IDL109" to source.span("table<bool>")), located(source))
    }

    @Test
    fun unknownIdTargetIsLocated() {
        val source = "record A { e: id<Nope> }"
        assertEquals(listOf("IDL101" to source.span("id<Nope>")), located(source))
    }
}
