package xyz.malefic.parz.idl.syntax

import com.ionspin.kotlin.bignum.integer.BigInteger
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
                        7,
                        [
                            FieldSyntax(
                                "amount",
                                17,
                                TypeExpr.Primitive(Type.Decimal(BigInteger(-999999999), BigInteger(999999999), 2), 25),
                            ),
                            FieldSyntax("status", 40, TypeExpr.Named("Status", 48)),
                        ],
                    ),
                    DeclSyntax.Enum(
                        "Status",
                        62,
                        [
                            MemberSyntax("pending", 71, 0, null),
                            MemberSyntax("settled", 80, 5, 90),
                        ],
                    ),
                    DeclSyntax.Alias("Payments", 99, TypeExpr.Seq(TypeExpr.Named("Payment", 114), 110)),
                ],
            ),
            parse(
                lex(
                    """
                    record Payment { amount: decimal(9, 2), status: Status }
                    enum Status { pending, settled = 5 }
                    type Payments = seq<Payment>
                    """.trimIndent(),
                ),
            ),
        )
    }
}
