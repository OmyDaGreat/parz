package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Type.Collection.Option
import xyz.malefic.parz.idl.Type.Collection.Seq
import xyz.malefic.parz.idl.Type.Primitive.Bool
import xyz.malefic.parz.idl.Type.Primitive.Str
import kotlin.test.Test
import kotlin.test.assertEquals

class Refs {
    @Test
    fun refs() {
        assertEquals(setOf("A"), Type.Ref("A").refs())
        assertEquals(setOf("A"), Seq(Option(Type.Ref("A"))).refs())
        assertEquals(emptySet(), Bool.refs())
        assertEquals(emptySet(), Seq(Str()).refs())
    }

    @Test
    fun generatorDistinct() {
        assertEquals("foobar", "Foo_Bar".generatorDistinct())
        assertEquals("foobar", "fooBar".generatorDistinct())
        assertEquals("foobar", "__foo__bar__".generatorDistinct())
    }
}
