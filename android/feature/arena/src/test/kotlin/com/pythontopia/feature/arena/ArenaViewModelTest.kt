package com.pythontopia.feature.arena

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArenaViewModelTest {
    @Test
    fun `initial state has isLoading false`() {
        val vm = ArenaViewModel()
        assertEquals(false, vm.state.value.isLoading)
    }
}
