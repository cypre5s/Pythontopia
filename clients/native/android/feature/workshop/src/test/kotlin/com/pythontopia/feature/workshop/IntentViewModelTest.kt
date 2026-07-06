package com.pythontopia.feature.workshop

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntentViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: IntentRepository = mockk(relaxed = false)
    private lateinit var viewModel: IntentViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = IntentViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `route classifies review and exposes open review dispatch`() = runTest {
        coEvery { repository.route("我想复习 for 循环") } returns IntentRouteResponse(
            intent = "REVIEW",
            kcCodes = listOf("PY43-KC-001"),
            focusHint = "复盘 for 循环",
            confidence = 0.7,
            reason = "用户想复习巩固",
        )

        viewModel.onInputChange("我想复习 for 循环")
        viewModel.route()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertInstanceOf(IntentDispatch.OpenReview::class.java, state.pendingDispatch)
        assertEquals("", state.input)
        assertEquals(1, state.history.size)
        assertEquals(IntentKind.REVIEW, state.lastResponse?.kind)
    }

    @Test
    fun `blank input does not call repository`() = runTest {
        viewModel.onInputChange("   ")
        viewModel.route()
        advanceUntilIdle()

        assertNull(viewModel.state.value.pendingDispatch)
        assertNull(viewModel.state.value.lastResponse)
    }

    @Test
    fun `route failure surfaces error message`() = runTest {
        coEvery { repository.route(any()) } throws RuntimeException("graph down")

        viewModel.onInputChange("出题")
        viewModel.route()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("graph down"))
        assertNull(state.pendingDispatch)
    }

    @Test
    fun `consumeDispatch clears one-shot navigation signal`() = runTest {
        coEvery { repository.route(any()) } returns IntentRouteResponse(intent = "ASK", confidence = 0.6)
        viewModel.onInputChange("什么是缩进")
        viewModel.route()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.pendingDispatch)

        viewModel.consumeDispatch()

        assertNull(viewModel.state.value.pendingDispatch)
    }
}
