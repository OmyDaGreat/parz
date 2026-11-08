package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
import xyz.malefic.parz.core.SourceSpan
import xyz.malefic.parz.idl.Type
import kotlin.test.Test
import kotlin.test.assertEquals

class ParserTest {
    @Test
    fun testByExample() {
        assertEquals(
            SchemaSyntax(
                [
                    DeclSyntax.Record(
                        "Payment",
                        SourceSpan(7, 13),
                        [
                            FieldSyntax(
                                "amount",
                                SourceSpan(17, 22),
                                TypeExpr.Primitive(Type.Decimal(BigInteger(-999999999), BigInteger(999999999), 2), SourceSpan(25, 39)),
                            ),
                            FieldSyntax("status", SourceSpan(40, 48), TypeExpr.Named("Status", SourceSpan(40, 48))),
                        ],
                    ),
                    DeclSyntax.Enum(
                        "Status",
                        SourceSpan(62, 90),
                        [
                            MemberSyntax("pending", SourceSpan(71, 78), 0, null),
                            MemberSyntax("settled", SourceSpan(80, 87), 5, SourceSpan(90, 90)),
                        ],
                    ),
                    DeclSyntax.Alias(
                        "Payments",
                        SourceSpan(99, 110),
                        TypeExpr.Seq(TypeExpr.Named("Payment", SourceSpan(114, 121)), SourceSpan(110, 110)),
                    ),
                ],
            ),
            parse {
                lex(
                    """
                    record Payment { amount: decimal(9, 2), status: Status }
                    enum Status { pending, settled = 5 }
                    type Payments = seq<Payment>
                    """.trimIndent(),
                )
            }.value,
        )
    }
}
