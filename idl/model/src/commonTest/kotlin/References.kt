package xyz.malefic.parz.idl.model

import xyz.malefic.parz.core.Severity
import xyz.malefic.parz.core.collecting
import xyz.malefic.parz.idl.model.Decl.Record.Field
import xyz.malefic.parz.idl.model.Decl.Variant.Member
import xyz.malefic.parz.idl.model.SchemaIssueKind.FieldPath.RecordPath
import xyz.malefic.parz.idl.model.SchemaIssueKind.FieldPath.VariantPath
import xyz.malefic.parz.idl.model.SchemaIssueKind.InvalidTarget
import xyz.malefic.parz.idl.model.SchemaIssueKind.MissingTable
import xyz.malefic.parz.idl.model.Type.Collection.Map
import xyz.malefic.parz.idl.model.Type.Collection.Option
import xyz.malefic.parz.idl.model.Type.Collection.Seq
import xyz.malefic.parz.idl.model.Type.Collection.Table
import xyz.malefic.parz.idl.model.Type.Primitive.Bool
import xyz.malefic.parz.idl.model.Type.Primitive.Id
import xyz.malefic.parz.idl.model.Type.Primitive.Str
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `id<T>` and `table<T>`: value checks, schema validation (IDL108, IDL109), and [checkReferences].
 */
class References {
    /**
     * ```
     * variant Stmt { goTo(target: id<Para>), stop }
     * record Para { body: seq<Stmt> }
     * record Prog { paras: table<Para>, entry: id<Para>, facts: map<id<Para>, bool> }
     * ```
     */
    private val schema =
        Schema(
            listOf(
                Decl.Variant("Stmt", listOf(Member("goTo", 0, listOf(Field("target", Id("Para")))), Member("stop", 1))),
                Decl.Record("Para", listOf(Field("body", Seq(Type.Ref("Stmt"))))),
                Decl.Record(
                    "Prog",
                    listOf(Field("paras", Table(Type.Ref("Para"))), Field("entry", Id("Para")), Field("facts", Map(Id("Para"), Bool))),
                ),
            ),
        )

    private val prog = Type.Ref("Prog")

    private val stop = Value.Variant("stop")

    private fun goTo(n: Int) = Value.Variant("goTo", mapOf("target" to Value.Id(n)))

    private fun para(vararg stmts: Value) = Value.Record(mapOf("body" to Value.Seq(stmts.toList())))

    private fun prog(
        paras: List<Value>,
        entry: Int = 0,
        facts: kotlin.collections.Map<Value, Value> = emptyMap(),
    ) = Value.Record(mapOf("paras" to Value.Seq(paras), "entry" to Value.Id(entry), "facts" to Value.Map(facts)))

    private fun references(
        value: Value,
        type: Type = prog,
    ) = checkReferences(value, type, schema)

    private val goToTarget = listOf(Segment.Member("goTo"), Segment.Field("target"))

    // Value checks

    @Test
    fun ids() {
        Id("Para").assertAccepts(Value.Id(0), Value.Id(Int.MAX_VALUE))
        assertEquals(listOf(mismatch(Reason.Fit.NegativeId(-1))), Id("Para") check Value.Id(-1))
        Id("Para").assertRejectsOtherKinds()
    }

    @Test
    fun tablesHoldListsAndCheckEveryEntry() {
        val type = Table(Bool)
        type.assertAccepts(Value.Seq(emptyList()), Value.Seq(listOf(Value.Bool(true))))
        assertEquals(
            listOf(Mismatch(listOf(Segment.Index(1)), Reason.WrongKind("bool", "string"))),
            type check Value.Seq(listOf(Value.Bool(true), Value.Str("x"))),
        )
        type.assertRejectsOtherKinds(setOf("seq"))
    }

    @Test
    fun checkIntoStartsAtTheGivenPath() {
        val prefix = listOf(Segment.Field("outer"))
        val reported = collecting<Unit, Mismatch> { Bool.checkInto(Value.Str("x"), Schema.EMPTY, prefix) }.diagnostics
        assertEquals(listOf(Mismatch(prefix, Reason.WrongKind("bool", "string"))), reported)
    }

    @Test
    fun mapEntryPathsNameTheKey() =
        assertEquals(
            listOf(Mismatch(listOf(Segment.Entry(Value.Str("k"))), Reason.WrongKind("bool", "string"))),
            Map(Str(), Bool) check Value.Map(mapOf(Value.Str("k") to Value.Str("x"))),
        )

    // Reference checks

    @Test
    fun validReferences() {
        val value = prog(listOf(para(goTo(1)), para(stop, goTo(0))), entry = 1, facts = mapOf(Value.Id(0) to Value.Bool(true)))
        assertEquals(emptyList(), prog.check(value, schema))
        assertEquals(emptyList(), references(value))
    }

    @Test
    fun emptyTableWithNoIdsIsFine() = assertEquals(emptyList(), references(Value.Seq(emptyList()), Table(Type.Ref("Para"))))

    @Test
    fun danglingIdInsideATableEntry() =
        assertEquals(
            listOf(
                Mismatch(
                    listOf(Segment.Field("paras"), Segment.Index(0), Segment.Field("body"), Segment.Index(1)) + goToTarget,
                    Reason.DanglingId("Para", Value.Id(5), 1),
                ),
            ),
            references(prog(listOf(para(stop, goTo(5))))),
        )

    @Test
    fun idEqualToTheSizeIsDangling() =
        assertEquals(
            listOf(Reason.DanglingId("Para", Value.Id(2), 2)),
            references(prog(listOf(para(), para()), entry = 2)).map { it.reason },
        )

    @Test
    fun danglingRootIdAndMapKey() =
        assertEquals(
            listOf(
                Mismatch(listOf(Segment.Field("entry")), Reason.DanglingId("Para", Value.Id(1), 1)),
                Mismatch(listOf(Segment.Field("facts"), Segment.Key(Value.Id(3))), Reason.DanglingId("Para", Value.Id(3), 1)),
            ),
            references(prog(listOf(para()), entry = 1, facts = mapOf(Value.Id(3) to Value.Bool(true)))),
        )

    @Test
    fun idsWithoutATableUnderTheRoot() =
        assertEquals(
            listOf(Mismatch(listOf(Segment.Field("body"), Segment.Index(0)) + goToTarget, Reason.MissingTable("Para"))),
            references(para(goTo(0)), Type.Ref("Para")),
        )

    @Test
    fun oneTablePerTargetUnderTheRoot() =
        assertEquals(
            listOf(Mismatch(listOf(Segment.Index(1), Segment.Field("paras")), Reason.DuplicateTable("Para"))),
            references(Value.Seq(listOf(prog(listOf(para())), prog(listOf(para())))), Seq(prog)),
        )

    @Test
    fun onlyTheHeldMemberIsWalked() = assertEquals(emptyList(), references(prog(listOf(para(stop)))))

    @Test
    fun optionalIds() {
        val holder =
            Schema(
                listOf(
                    Decl.Record("P", listOf(Field("x", Bool))),
                    Decl.Record("H", listOf(Field("ps", Table(Type.Ref("P"))), Field("o", Option(Id("P"))))),
                ),
            )

        fun h(o: Value) = Value.Record(mapOf("ps" to Value.Seq(emptyList()), "o" to o))
        assertEquals(emptyList(), checkReferences(h(Value.None), Type.Ref("H"), holder))
        assertEquals(
            listOf(Mismatch(listOf(Segment.Field("o"), Segment.Inner), Reason.DanglingId("P", Value.Id(0), 0))),
            checkReferences(h(Value.Some(Value.Id(0))), Type.Ref("H"), holder),
        )
    }

    // Schema validation

    private fun kinds(vararg decls: Decl) = validate(decls.toList()).diagnostics.map { it.kind }

    private val p = Decl.Record("P", listOf(Field("x", Bool)))

    @Test
    fun idWithATableIsValid() =
        assertEquals(emptyList(), kinds(p, Decl.Record("A", listOf(Field("t", Table(Type.Ref("P"))), Field("e", Id("P"))))))

    @Test
    fun tableMayBeInAnotherDeclaration() {
        val holder = Decl.Record("Holder", listOf(Field("t", Table(Type.Ref("P")))))
        assertEquals(emptyList(), kinds(p, holder, Decl.Record("A", listOf(Field("e", Option(Id("P")))))))
    }

    @Test
    fun idWithoutATable() {
        val field = Field("e", Seq(Id("P")))
        val decl = Decl.Record("A", listOf(field))
        val kinds = kinds(p, decl)
        assertEquals(listOf(MissingTable("P", decl, RecordPath(field))), kinds)
        assertEquals("IDL108", kinds.single().code)
        assertEquals("No table<P> for id<P> in field e", kinds.single().message)
        assertEquals(Severity.WARNING, kinds.single().severity)
    }

    @Test
    fun idWithoutATableInMemberMapKeyAndAlias() {
        val field = Field("m", Map(Id("P"), Bool))
        val member = Member("a", 0, listOf(field))
        val variant = Decl.Variant("V", listOf(member))
        val alias = Decl.Alias("T", Id("P"))
        assertEquals(
            listOf(MissingTable("P", variant, VariantPath(member, field)), MissingTable("P", alias, null)),
            kinds(p, variant, alias),
        )
    }

    @Test
    fun targetsMustBeRecordsOrVariants() {
        val q = Decl.Alias("Q", Type.Ref("P"))
        val table = Field("t", Table(Type.Ref("Q")))
        val id = Field("e", Id("Q"))
        val decl = Decl.Record("A", listOf(table, id))
        assertEquals(
            listOf(InvalidTarget(Table(Type.Ref("Q")), decl, RecordPath(table)), InvalidTarget(Id("Q"), decl, RecordPath(id))),
            kinds(p, q, decl),
        )
    }

    @Test
    fun tablesOfUnnamedTypes() {
        val field = Field("t", Seq(Table(Bool)))
        val decl = Decl.Record("A", listOf(field))
        val kinds = kinds(decl)
        assertEquals(listOf(InvalidTarget(Table(Bool), decl, RecordPath(field))), kinds)
        assertEquals("IDL109", kinds.single().code)
        assertEquals("Table target must be a record or variant in field t: bool", kinds.single().message)
    }

    @Test
    fun variantTargetsAreFine() {
        val v = Decl.Variant("V", listOf(Member("a", 0)))
        assertEquals(emptyList(), kinds(v, Decl.Record("A", listOf(Field("t", Table(Type.Ref("V"))), Field("e", Id("V"))))))
    }

    @Test
    fun unknownTargetsAreOnlyUnknown() =
        assertEquals(
            listOf("IDL101", "IDL101"),
            kinds(Decl.Record("A", listOf(Field("t", Table(Type.Ref("Nope"))), Field("e", Id("Nope"))))).map { it.code },
        )

    @Test
    fun selfReferenceIsNotACycle() =
        assertEquals(emptyList(), kinds(Decl.Record("N", listOf(Field("next", Option(Id("N"))), Field("all", Table(Type.Ref("N")))))))
}
