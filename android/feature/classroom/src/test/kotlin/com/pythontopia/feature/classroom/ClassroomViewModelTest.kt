package com.pythontopia.feature.classroom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassroomViewModelTest {
    @Test
    fun `initial state has isLoading false`() {
        val vm = ClassroomViewModel()
        assertEquals(false, vm.state.value.isLoading)
    }
}
