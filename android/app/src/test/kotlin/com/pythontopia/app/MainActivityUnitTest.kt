package com.pythontopia.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainActivityUnitTest {

    @Test
    fun packageName_isCorrect() {
        assertEquals(
            "com.pythontopia.app",
            PythontopiaApplication::class.java.`package`.name
        )
    }
}
