package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Type.Primitive.Void
import kotlin.test.Test

class Void {
    @Test fun nonVoid() = Void.assertRejectsOtherKinds()

    @Test fun isVoid() = Void.assertAccepts(Value.Void)
}
