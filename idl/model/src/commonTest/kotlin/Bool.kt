package xyz.malefic.parz.idl

import xyz.malefic.parz.idl.Type.Primitive.Bool
import kotlin.test.Test

class Bool {
    @Test fun nonBool() = Bool.assertRejectsOtherKinds()

    @Test fun isBool() = Bool.assertAccepts(Value.Bool(true), Value.Bool(false))
}
