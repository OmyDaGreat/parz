package xyz.malefic.parz.idl

import kotlin.test.Test

class Bool {
    @Test fun nonBool() = Type.Bool.assertRejectsOtherKinds()

    @Test fun isBool() = Type.Bool.assertAccepts(Value.Bool(true), Value.Bool(false))
}
