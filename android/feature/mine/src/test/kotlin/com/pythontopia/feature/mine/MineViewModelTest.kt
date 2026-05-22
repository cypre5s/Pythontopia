package com.pythontopia.feature.mine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MineViewModelTest {
    @Test
    fun `initial state has isLoading false`() {
        val vm = MineViewModel()
        assertEquals(false, vm.state.value.isLoading)
    }
}
