package com.pythontopia.feature.auth

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onUsernameChange updates username and keeps other fields untouched`() {
        viewModel.onUsernameChange("alice")

        val state = viewModel.state.value
        assertEquals("alice", state.username)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.success)
    }

    @Test
    fun `login flips isLoading then resolves to success`() = runTest {
        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)
            assertFalse(initial.success)

            viewModel.login()

            val loading = awaitItem()
            assertTrue(loading.isLoading)
            assertFalse(loading.success)

            advanceUntilIdle()

            val done = awaitItem()
            assertFalse(done.isLoading)
            assertTrue(done.success)
            assertNull(done.errorMessage)
        }
    }
}
