package com.pythontopia.feature.path

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PathViewModelTest {
    @Test
    fun `initial state has isLoading false`() {
        val vm = PathViewModel()
        assertEquals(false, vm.state.value.isLoading)
    }
}
