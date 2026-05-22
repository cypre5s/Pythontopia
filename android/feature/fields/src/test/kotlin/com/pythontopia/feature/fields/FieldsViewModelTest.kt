package com.pythontopia.feature.fields

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldsViewModelTest {
    @Test
    fun `initial state has isLoading false`() {
        val vm = FieldsViewModel()
        assertEquals(false, vm.state.value.isLoading)
    }
}
