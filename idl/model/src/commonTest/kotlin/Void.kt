package xyz.malefic.parz.idl

import kotlin.test.Test

class Void {
    @Test fun nonVoid() = Type.Void.assertRejectsOtherKinds()

    @Test fun isVoid() = Type.Void.assertAccepts(Value.Void)
}
