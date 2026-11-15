package xyz.malefic.parz.idl

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.idl.Decl.Record.Field
import kotlin.test.Test
import kotlin.test.assertEquals

class Alias {
    private val money = Type.Decimal(BigInteger(-9999), BigInteger(9999), 2)

    @Test
    fun aliasToPrimitive() {
        val schema = Schema(listOf(Decl.Alias("Money", money)))
        Type.Ref("Money").assertAccepts(dec(1234, 2), schema = schema)
        assertEquals(listOf(mismatch(Reason.WrongKind("decimal", "bool"))), Type.Ref("Money").check(Value.Bool(true), schema))
    }

    @Test
    fun aliasChain() {
        val schema = Schema(listOf(Decl.Alias("A", Type.Ref("B")), Decl.Alias("B", Type.Bool)))
        Type.Ref("A").assertAccepts(Value.Bool(true), schema = schema)
        Type.Ref("A").assertRejects(Value.Str(""), schema = schema)
    }

    @Test
    fun aliasToRecord() {
        val schema = Schema(listOf(Decl.Alias("Row", Type.Ref("R")), Decl.Record("R", listOf(Field("x", Type.Bool)))))
        Type.Ref("Row").assertAccepts(Value.Record(mapOf("x" to Value.Bool(true))), schema = schema)
        Type.Ref("Row").assertRejects(Value.Record(emptyMap()), schema = schema)
    }

    @Test
    fun aliasToContainer() {
        val schema = Schema(listOf(Decl.Alias("Flags", Type.Seq(Type.Option(Type.Bool)))))
        Type.Ref("Flags").assertAccepts(Value.Seq(listOf(Value.None, Value.Some(Value.Bool(true)))), schema = schema)
        assertEquals(
            listOf(Mismatch(listOf(Segment.Index(0), Segment.Inner), Reason.WrongKind("bool", "string"))),
            Type.Ref("Flags").check(Value.Seq(listOf(Value.Some(Value.Str("")))), schema),
        )
    }
}
