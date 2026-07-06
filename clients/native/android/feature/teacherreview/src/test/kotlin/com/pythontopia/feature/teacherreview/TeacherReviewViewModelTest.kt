package com.pythontopia.feature.teacherreview

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeacherReviewViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: TeacherReviewRepository = mockk(relaxed = true)
    private lateinit var viewModel: TeacherReviewViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TeacherReviewViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetch 把题加入队列并选中`() = runTest {
        coEvery { repository.fetchProblem(1, 1) } returns Result.success(problem("boss-1-1"))

        viewModel.fetch()
        advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.loading)
        assertEquals(1, s.entries.size)
        assertEquals("boss-1-1", s.selected?.problem?.problemId)
        assertEquals(1, s.pending)
    }

    @Test
    fun `approve 把当前条目标记为通过`() = runTest {
        coEvery { repository.fetchProblem(1, 1) } returns Result.success(problem("boss-1-1"))
        viewModel.fetch()
        advanceUntilIdle()
        val key = viewModel.state.value.selectedKey!!

        viewModel.approve(key)

        val s = viewModel.state.value
        assertEquals(ReviewDecision.APPROVED, s.selected?.decision)
        assertEquals(1, s.approved)
    }

    @Test
    fun `reject 记录驳回原因_空原因回退默认`() = runTest {
        coEvery { repository.fetchProblem(2, 3) } returns Result.success(problem("boss-2-3"))
        viewModel.setChapter(2)
        viewModel.setStage(3)
        viewModel.fetch()
        advanceUntilIdle()
        val key = viewModel.state.value.selectedKey!!

        viewModel.reject(key, "   ")

        val entry = viewModel.state.value.selected
        assertEquals(ReviewDecision.REJECTED, entry?.decision)
        assertEquals("未提供原因", entry?.reason)
        assertEquals(1, viewModel.state.value.rejected)
    }

    @Test
    fun `fetch 失败写入错误`() = runTest {
        coEvery { repository.fetchProblem(1, 1) } returns Result.failure(RuntimeException("graph 超时"))

        viewModel.fetch()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error?.contains("graph 超时") == true)
        assertTrue(viewModel.state.value.entries.isEmpty())
    }

    @Test
    fun `clearAll 清空队列`() = runTest {
        coEvery { repository.fetchProblem(1, 1) } returns Result.success(problem("boss-1-1"))
        viewModel.fetch()
        advanceUntilIdle()

        viewModel.clearAll()

        assertTrue(viewModel.state.value.entries.isEmpty())
    }

    private fun problem(id: String) = ReviewProblem(
        problemId = id,
        chapter = 1,
        stage = 1,
        difficulty = 2,
        kcCode = "PY43-KC-001",
        statement = "题面 $id",
        hints = listOf("提示"),
        sampleInput = "1",
        sampleOutput = "1",
        totalTestCases = 5,
        reviewed = true,
        aiGenerated = true,
    )
}
