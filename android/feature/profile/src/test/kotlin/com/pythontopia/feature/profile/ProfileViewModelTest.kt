package com.pythontopia.feature.profile

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileViewModelTest {

    @Test
    fun `initial state has no profile and is not loading`() {
        val viewModel = ProfileViewModel()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.profile)
    }
}
