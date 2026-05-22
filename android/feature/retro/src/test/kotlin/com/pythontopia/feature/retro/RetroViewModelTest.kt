package com.pythontopia.feature.retro

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetroViewModelTest {
    @Test
    fun `initial state has isLoading false`() {
        val vm = RetroViewModel()
        assertEquals(false, vm.state.value.isLoading)
    }
}
