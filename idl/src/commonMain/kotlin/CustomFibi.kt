package xyz.malefic.parz.idl

fun generateFibi() =
    sequence {
        var a = 0
        yield(0)
        var b = 1
        yield(b)
        while (true) {
            val c = a + b
            yield(c)
            a = b
            b = c
        }
    }
