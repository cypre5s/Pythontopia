package com.pythontopia.feature.workshop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkshopViewModelTest {
    @Test
    fun `initial state has isLoading false`() {
        val vm = WorkshopViewModel()
        assertEquals(false, vm.state.value.isLoading)
    }
}
