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
                        SourceSpan(0, 14),
                        [
                            FieldSyntax(
                                "amount",
                                SourceSpan(17, 23),
                                TypeSyntax.Primitive(Type.Decimal(BigInteger(-999999999), BigInteger(999999999), 2), SourceSpan(25, 38)),
                            ),
                            FieldSyntax("status", SourceSpan(40, 46), TypeSyntax.Named("Status", SourceSpan(48, 54))),
                        ],
                    ),
                    DeclSyntax.Enum(
                        "Status",
                        SourceSpan(57, 68),
                        [
                            MemberSyntax("pending", SourceSpan(71, 78), 0, null),
                            MemberSyntax("settled", SourceSpan(80, 87), 5, SourceSpan(90, 91)),
                        ],
                    ),
                    DeclSyntax.Alias(
                        "Payments",
                        SourceSpan(94, 107),
                        TypeSyntax.Seq(TypeSyntax.Named("Payment", SourceSpan(114, 121)), SourceSpan(110, 121)),
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
