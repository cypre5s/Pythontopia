package com.pythontopia.core.common

import com.pythontopia.core.common.learner.MasteryLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MasteryLevelTest {

    @Test
    fun `score 0_0 falls into novice bucket`() {
        assertEquals("novice", MasteryLevel(0.0).bucket)
    }

    @Test
    fun `score just below 0_3 stays in novice bucket`() {
        assertEquals("novice", MasteryLevel(0.29).bucket)
    }

    @Test
    fun `score 0_3 to just below 0_6 falls into intermediate bucket`() {
        assertEquals("intermediate", MasteryLevel(0.3).bucket)
        assertEquals("intermediate", MasteryLevel(0.59).bucket)
    }

    @Test
    fun `score 0_6 to just below 0_85 falls into proficient bucket`() {
        assertEquals("proficient", MasteryLevel(0.6).bucket)
        assertEquals("proficient", MasteryLevel(0.84).bucket)
    }

    @Test
    fun `score 0_85 and above including 1_0 falls into mastery bucket`() {
        assertEquals("mastery", MasteryLevel(0.85).bucket)
        assertEquals("mastery", MasteryLevel(1.0).bucket)
    }

    @Test
    fun `score outside 0_0 to 1_0 throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { MasteryLevel(-0.01) }
        assertThrows(IllegalArgumentException::class.java) { MasteryLevel(1.01) }
    }
}
