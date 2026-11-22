package xyz.malefic.parz.idl.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Finding where in the source a schema error belongs, with [locate].
 */
class Locating {
    @Test
    fun unknownTypeInRecord() {
        val source = "record A { x: seq<Nope> }"
        assertEquals(listOf("IDL101" to source.span("Nope")), located(source))
    }

    @Test
    fun unknownTypeInMember() {
        val source = "variant V { a(x: option<Nope>) }"
        assertEquals(listOf("IDL101" to source.span("Nope")), located(source))
    }

    @Test
    fun unknownTypeInAlias() {
        val source = "type T = seq<Nope>"
        assertEquals(listOf("IDL101" to source.span("Nope")), located(source))
    }

    @Test
    fun duplicateRecordField() {
        val source = "record A { x: bool, x: string }"
        assertEquals(listOf("IDL102" to source.span("x", 1)), located(source))
    }

    @Test
    fun duplicateMemberField() {
        val source = "variant V { m(x: bool, x: bool) }"
        assertEquals(listOf("IDL102" to source.span("x", 1)), located(source))
    }

    @Test
    fun duplicateMemberName() {
        val source = "variant V { dup, dup }"
        assertEquals(listOf("IDL103" to source.span("dup", 1)), located(source))
    }

    @Test
    fun duplicateExplicitTag() {
        val source = "variant V { one = 1, two = 1 }"
        assertEquals(listOf("IDL104" to source.span("1", 1)), located(source))
    }

    @Test
    fun duplicateAutoTag() {
        val source = "variant V { one = 1, zero = 0, next }"
        assertEquals(listOf("IDL104" to source.span("next")), located(source))
    }

    @Test
    fun cycle() {
        val source = "record A { b: B }\nrecord B { a: A }"
        assertEquals(listOf("IDL105" to source.span("record A")), located(source))
    }

    @Test
    fun duplicateDecl() {
        val source = "record A { x: bool }\nrecord A { y: bool }"
        assertEquals(listOf("IDL106" to source.span("record A", 1)), located(source))
    }

    @Test
    fun invalidMapKeyInRecord() {
        val source = "record A { m: map<seq<bool>, string> }"
        assertEquals(listOf("IDL107" to source.span("seq<bool>")), located(source))
    }

    @Test
    fun invalidMapKeyNestedInMember() {
        val source = "variant V { a(m: option<map<string, map<P, bool>>>) } record P { x: bool }"
        assertEquals(listOf("IDL107" to source.span("P")), located(source))
    }

    @Test
    fun invalidMapKeyInAlias() {
        val source = "type T = seq<map<option<bool>, bool>>"
        assertEquals(listOf("IDL107" to source.span("option<bool>")), located(source))
    }

    @Test
    fun enumMapKeyIsFine() = assertEquals(emptyList(), located("enum S { a, b } record A { m: map<S, bool> }"))
}
