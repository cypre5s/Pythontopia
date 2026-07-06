package com.pythontopia.feature.mainline

import com.pythontopia.core.data.dto.mainline.ChapterTestPaperDto
import com.pythontopia.core.data.dto.mainline.TestPrepareResponse
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterTestViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: MainlineRepository = mockk(relaxed = true)
    private lateinit var viewModel: ChapterTestViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChapterTestViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `quiz 模式经 quiz 端点开考`() = runTest {
        coEvery { repository.prepareTest(2, ChapterAssessment.QUIZ) } returns TestPrepareResponse("READY")
        coEvery { repository.startTest(2, ChapterAssessment.QUIZ) } returns paper(testId = 99L, chapter = 2)

        viewModel.load(2, ChapterAssessment.QUIZ)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(ChapterAssessment.QUIZ, s.assessment)
        assertEquals(99L, s.paper?.testId)
        coVerify { repository.startTest(2, ChapterAssessment.QUIZ) }
    }

    @Test
    fun `默认 test 模式经 test 端点开考`() = runTest {
        coEvery { repository.startTest(1, ChapterAssessment.TEST) } returns paper(testId = 7L, chapter = 1)

        viewModel.load(1)
        advanceUntilIdle()

        assertEquals(ChapterAssessment.TEST, viewModel.state.value.assessment)
        coVerify { repository.startTest(1, ChapterAssessment.TEST) }
    }

    @Test
    fun `submit 沿用当前测评模式`() = runTest {
        coEvery { repository.startTest(3, ChapterAssessment.QUIZ) } returns paper(testId = 42L, chapter = 3)
        coEvery { repository.submitTest(any(), any(), any(), any()) } returns mockk(relaxed = true)
        viewModel.load(3, ChapterAssessment.QUIZ)
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        coVerify { repository.submitTest(3, 42L, any(), ChapterAssessment.QUIZ) }
    }

    private fun paper(testId: Long, chapter: Int) = ChapterTestPaperDto(
        testId = testId,
        chapter = chapter,
        attemptNo = 1,
        status = "IN_PROGRESS",
        timeLimitSec = 600,
        deadlineAt = null,
        items = emptyList(),
    )
}
