package xyz.malefic.parz.idl

import kotlin.test.Test
import kotlin.test.assertEquals

class Refs {
    @Test
    fun refs() {
        assertEquals(setOf("A"), Type.Ref("A").refs())
        assertEquals(setOf("A"), Type.Seq(Type.Option(Type.Ref("A"))).refs())
        assertEquals(emptySet(), Type.Bool.refs())
        assertEquals(emptySet(), Type.Seq(Type.Str()).refs())
    }

    @Test
    fun generatorDistinct() {
        assertEquals("foobar", "Foo_Bar".generatorDistinct())
        assertEquals("foobar", "fooBar".generatorDistinct())
        assertEquals("foobar", "__foo__bar__".generatorDistinct())
    }
}
