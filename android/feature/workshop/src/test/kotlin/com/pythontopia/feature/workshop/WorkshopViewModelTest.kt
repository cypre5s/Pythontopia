package com.pythontopia.feature.workshop

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: WorkshopRepository = mockk(relaxUnitFun = true)
    private lateinit var viewModel: WorkshopViewModel

    private val sampleProblem = ProblemDto(
        id = 476L,
        kcCode = "PY43-KC-016",
        title = "圆面积计算",
        statementMarkdown = "## 题目\n计算圆面积",
        inputDescription = "radius",
        outputDescription = "area",
        difficulty = "Low",
        samples = listOf(ProblemSampleDto(input = "25", output = "1963.4375")),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.history(any()) } returns emptyList()
        viewModel = WorkshopViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default KC and starter code`() {
        val state = viewModel.state.value
        assertEquals(WorkshopUiState.DEFAULT_KC_CODE, state.kcCodeFilter)
        assertEquals(WorkshopUiState.DEFAULT_STARTER_CODE, state.userCode)
        assertNull(state.currentProblem)
        assertNull(state.latestStatus)
        assertFalse(state.isPickerOpen)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `openPicker loads problems and exposes them in state`() = runTest {
        coEvery { repository.problems("PY43-KC-016") } returns listOf(sampleProblem)

        viewModel.openPicker()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isPickerOpen)
        assertEquals(1, state.problems.size)
        assertEquals(sampleProblem.id, state.problems.first().id)
        assertNull(state.errorMessage)
        coVerify { repository.problems("PY43-KC-016") }
    }

    @Test
    fun `loadProblems empty result sets errorMessage`() = runTest {
        coEvery { repository.problems(any()) } returns emptyList()

        viewModel.loadProblems()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.problems.isEmpty())
        assertNotNull(state.errorMessage)
        assertFalse(state.isLoadingProblems)
    }

    @Test
    fun `changeKcFilter while picker open triggers reload`() = runTest {
        coEvery { repository.problems("PY43-KC-016") } returns listOf(sampleProblem)
        coEvery { repository.problems("PY43-KC-022") } returns emptyList()

        viewModel.openPicker()
        advanceUntilIdle()
        viewModel.changeKcFilter("PY43-KC-022")
        advanceUntilIdle()

        assertEquals("PY43-KC-022", viewModel.state.value.kcCodeFilter)
        coVerify { repository.problems("PY43-KC-022") }
    }

    @Test
    fun `changeKcFilter with same value does not trigger reload`() = runTest {
        coEvery { repository.problems(any()) } returns emptyList()

        viewModel.changeKcFilter(WorkshopUiState.DEFAULT_KC_CODE)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.problems(any()) }
    }

    @Test
    fun `selectProblem closes picker and resets editor with starter code`() = runTest {
        viewModel.updateCode("# stale code from previous problem")

        viewModel.selectProblem(sampleProblem)

        val state = viewModel.state.value
        assertEquals(sampleProblem, state.currentProblem)
        assertFalse(state.isPickerOpen)
        assertEquals(WorkshopUiState.DEFAULT_STARTER_CODE, state.userCode)
        assertNull(state.latestStatus)
    }

    @Test
    fun `selectProblem with same id preserves user code`() = runTest {
        viewModel.selectProblem(sampleProblem)
        viewModel.updateCode("print('keep me')")

        viewModel.selectProblem(sampleProblem)

        assertEquals("print('keep me')", viewModel.state.value.userCode)
    }

    @Test
    fun `updateCode writes to state without touching repository`() = runTest {
        viewModel.updateCode("print(1)")
        assertEquals("print(1)", viewModel.state.value.userCode)
        coVerify(exactly = 0) { repository.submit(any(), any()) }
    }

    @Test
    fun `submit without currentProblem is a no-op`() = runTest {
        viewModel.updateCode("print(1)")
        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.submit(any(), any()) }
        assertNull(viewModel.state.value.latestStatus)
    }

    @Test
    fun `submit success with terminal status fills latestStatus and refreshes history`() = runTest {
        val accepted = SubmissionDto(
            id = 999L,
            problemId = sampleProblem.id,
            code = "print(1)",
            status = SubmissionStatus.ACCEPTED,
            passedCases = 3,
            totalCases = 3,
            runtimeMs = 12,
        )
        coEvery { repository.submit(sampleProblem.id, any()) } returns accepted
        coEvery { repository.history(any()) } returns listOf(accepted)

        viewModel.selectProblem(sampleProblem)
        viewModel.updateCode("print(1)")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SubmissionStatus.ACCEPTED, state.latestStatus?.status)
        assertEquals(999L, state.currentSubmissionId)
        assertFalse(state.isPolling)
        assertFalse(state.isSubmitting)
        assertEquals(1, state.history.size)
        coVerify { repository.history(WorkshopViewModel.DEMO_HISTORY_OWNER_ID) }
    }

    @Test
    fun `submit non-terminal triggers polling until terminal status`() = runTest {
        val pending = SubmissionDto(
            id = 1234L,
            problemId = sampleProblem.id,
            code = "print(1)",
            status = SubmissionStatus.PENDING,
        )
        val running = pending.copy(status = SubmissionStatus.RUNNING)
        val wrong = pending.copy(
            status = SubmissionStatus.WRONG_ANSWER,
            passedCases = 1,
            totalCases = 3,
            traceback = "AssertionError",
        )
        coEvery { repository.submit(sampleProblem.id, any()) } returns pending
        every { repository.pollStatus(1234L, any(), any()) } returns flowOf(running, wrong)
        coEvery { repository.history(any()) } returns listOf(wrong)

        viewModel.selectProblem(sampleProblem)
        viewModel.updateCode("print(1)")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SubmissionStatus.WRONG_ANSWER, state.latestStatus?.status)
        assertEquals("AssertionError", state.latestStatus?.traceback)
        assertFalse(state.isPolling)
        coVerify { repository.history(WorkshopViewModel.DEMO_HISTORY_OWNER_ID) }
    }

    @Test
    fun `submit failure sets errorMessage and clears submitting flag`() = runTest {
        coEvery { repository.submit(any(), any()) } throws RuntimeException("boom")
        viewModel.selectProblem(sampleProblem)
        viewModel.updateCode("print(1)")

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("提交失败"))
    }

    @Test
    fun `polling failure surfaces in errorMessage and stops polling`() = runTest {
        val pending = SubmissionDto(
            id = 7L,
            problemId = sampleProblem.id,
            code = "print(1)",
            status = SubmissionStatus.PENDING,
        )
        coEvery { repository.submit(sampleProblem.id, any()) } returns pending
        every { repository.pollStatus(7L, any(), any()) } returns flow { throw RuntimeException("network down") }

        viewModel.selectProblem(sampleProblem)
        viewModel.updateCode("print(1)")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isPolling)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("轮询失败"))
    }

    @Test
    fun `canSubmit gate prevents double submit while polling`() = runTest {
        viewModel.selectProblem(sampleProblem)
        viewModel.updateCode("print(1)")
        assertTrue(viewModel.state.value.canSubmit)

        // Simulate that submit started and polling is in progress.
        coEvery { repository.submit(sampleProblem.id, any()) } returns SubmissionDto(
            id = 1L, problemId = sampleProblem.id, code = "print(1)",
            status = SubmissionStatus.PENDING,
        )
        every { repository.pollStatus(1L, any(), any()) } returns flow {
            awaitCancellation()
        }
        viewModel.submit()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `dismissError clears errorMessage`() = runTest {
        coEvery { repository.problems(any()) } returns emptyList()
        viewModel.loadProblems()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.dismissError()
        assertNull(viewModel.state.value.errorMessage)
    }
}
