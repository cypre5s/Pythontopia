package com.pythontopia.feature.mine

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MineEchoesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: MineRepository = mockk(relaxed = false)
    private lateinit var viewModel: MineEchoesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MineEchoesViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty and not loading`() {
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.echoes.isEmpty())
        assertTrue(state.errorBook.isEmpty())
        assertNull(state.pendingSessionId)
    }

    @Test
    fun `refresh loads echoes and error book`() = runTest {
        coEvery { repository.echoes() } returns listOf(sampleEcho("PY43-KC-016"))
        coEvery { repository.errorBook() } returns listOf(sampleErrorBook(555L))

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.echoes.size)
        assertEquals(1, state.errorBook.size)
        assertEquals("PY43-KC-016", state.echoes.first().kcCode)
    }

    @Test
    fun `refresh surfaces repository failure`() = runTest {
        coEvery { repository.echoes() } throws RuntimeException("offline")

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("offline"))
    }

    @Test
    fun `openEcho sets pendingSessionId on success`() = runTest {
        coEvery { repository.openEcho("PY43-KC-016") } returns sampleSession(900L)

        viewModel.openEcho("PY43-KC-016")
        advanceUntilIdle()

        assertEquals(900L, viewModel.state.value.pendingSessionId)
        assertNull(viewModel.state.value.openingKcCode)
        coVerify { repository.openEcho("PY43-KC-016") }
    }

    @Test
    fun `openEcho surfaces failure without navigation`() = runTest {
        coEvery { repository.openEcho(any()) } throws RuntimeException("no candidate")

        viewModel.openEcho("PY43-KC-099")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.pendingSessionId)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `consumePendingSession clears one-shot navigation signal`() = runTest {
        coEvery { repository.openEcho(any()) } returns sampleSession(900L)
        viewModel.openEcho("PY43-KC-016")
        advanceUntilIdle()
        assertEquals(900L, viewModel.state.value.pendingSessionId)

        viewModel.consumePendingSession()

        assertNull(viewModel.state.value.pendingSessionId)
    }

    private fun sampleEcho(kcCode: String): EchoCandidate = EchoCandidate(
        kcCode = kcCode,
        problemId = 476L,
        problemTitle = "圆面积调试",
        masteryScore = 0.4,
        confidence = 0.25,
        decayRisk = 0.8,
        minePriority = 0.7,
        recommendationReason = "遗忘回声 · 建议入矿",
    )

    private fun sampleErrorBook(sessionId: Long): ErrorBookItem = ErrorBookItem(
        sessionId = sessionId,
        submissionId = 9001L,
        problemId = 476L,
        questId = null,
        kcCode = "PY43-KC-016",
        problemTitle = "圆面积调试",
        status = MineSessionDetail.STATUS_OPEN,
        retryCount = 1,
        depthLayer = 2,
        profileDepthTarget = 4,
        profileGuidance = "建议下探到 L4",
        masteryScore = 0.4,
        minePriority = 0.7,
        decayRisk = 0.55,
        recommendationReason = "掌握薄弱 · 建议目标 L4",
        mistakePatterns = listOf("off_by_one"),
        patternMatched = true,
        variantProblem = MineVariantProblem(600L, "变式：椭圆面积"),
        depthSteps = emptyList(),
    )

    private fun sampleSession(sessionId: Long): MineSessionDetail = MineSessionDetail(
        sessionId = sessionId,
        status = MineSessionDetail.STATUS_OPEN,
        submissionId = 9001L,
        problemId = 476L,
        retryCount = 0,
        depthLayer = 4,
        profileDepthTarget = 4,
        masteryConfirmed = false,
        profileGuidance = null,
        variantProblem = null,
        depthSteps = emptyList(),
        cards = emptyList(),
        problem = null,
    )
}
