package com.pythontopia.feature.feynman

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeynmanViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: FeynmanRepository = mockk(relaxed = true)
    private lateinit var viewModel: FeynmanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FeynmanViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openSession 填充会话与误解并清空轮次`() = runTest {
        viewModel.onKcChange("PY43-KC-016")
        coEvery { repository.startSession("PY43-KC-016") } returns Result.success(
            FeynmanSessionDto(
                id = 7L,
                userId = 3L,
                kcCode = "PY43-KC-016",
                status = "OPEN",
                tuteeState = FeynmanTuteeStateDto(
                    misconceptions = listOf(
                        FeynmanMisconceptionDto("off-by-one", listOf(11L, 12L), false),
                    ),
                ),
            ),
        )

        viewModel.openSession()
        advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.loading)
        assertEquals(7L, s.currentSession?.id)
        assertEquals("PY43-KC-016", s.activeKc)
        assertEquals(1, s.misconceptions.size)
        assertTrue(s.turns.isEmpty())
    }

    @Test
    fun `teach 追加轮次并用 taughtFix 作为产出代码`() = runTest {
        givenOpenSession()
        coEvery { repository.teach(7L, any()) } returns Result.success(
            FeynmanTurnDto(id = 21L, role = "TUTEE", content = "我懂了", taughtFix = "print('ok')"),
        )

        viewModel.teach("因为 range(n) 不含 n，循环边界要减一")
        advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.teaching)
        assertEquals(1, s.turns.size)
        assertEquals("print('ok')", s.latestProducedCode)
    }

    @Test
    fun `judge 用当前误解的真实 event_id 合成证据链`() = runTest {
        givenOpenSession()
        coEvery { repository.teach(7L, any()) } returns Result.success(
            FeynmanTurnDto(id = 21L, role = "TUTEE", content = "懂了", taughtFix = "print('ok')"),
        )
        viewModel.teach("讲清原因后再改代码")
        advanceUntilIdle()
        coEvery { repository.judge(7L) } returns Result.success(
            FeynmanVerdictDto(sessionId = 7L, passed = true, message = "通过真判题"),
        )

        viewModel.judge()
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals("PASSED", s.latestJudgeResult?.status)
        assertEquals(listOf(11L, 12L), s.latestJudgeResult?.evidenceEventIds)
        assertEquals(listOf(11L, 12L), s.evidenceEventIds)
    }

    @Test
    fun `startSession 失败写入错误信息`() = runTest {
        viewModel.onKcChange("PY43-KC-099")
        coEvery { repository.startSession("PY43-KC-099") } returns
            Result.failure(RuntimeException("代表题缺失"))

        viewModel.openSession()
        advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.loading)
        assertNull(s.currentSession)
        assertTrue(s.error?.contains("代表题缺失") == true)
    }

    @Test
    fun `judge 在无产出代码时不触发`() = runTest {
        givenOpenSession()
        viewModel.judge()
        advanceUntilIdle()
        assertNull(viewModel.state.value.latestJudgeResult)
    }

    private fun givenOpenSession() {
        viewModel.onKcChange("PY43-KC-016")
        coEvery { repository.startSession("PY43-KC-016") } returns Result.success(
            FeynmanSessionDto(
                id = 7L,
                userId = 3L,
                kcCode = "PY43-KC-016",
                status = "OPEN",
                tuteeState = FeynmanTuteeStateDto(
                    misconceptions = listOf(
                        FeynmanMisconceptionDto("off-by-one", listOf(11L, 12L), false),
                    ),
                ),
            ),
        )
        viewModel.openSession()
    }
}
