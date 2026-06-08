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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MineSessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: MineRepository = mockk(relaxed = false)
    private lateinit var viewModel: MineSessionViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MineSessionViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load fetches session detail by id`() = runTest {
        coEvery { repository.sessionDetail(555L) } returns sessionAt(depthLayer = 2)

        viewModel.load(555L)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.session)
        assertEquals(2, state.depthLayer)
    }

    @Test
    fun `load rejects invalid id`() = runTest {
        viewModel.load(0L)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `revealCard calls reveal then reloads with deeper layer`() = runTest {
        coEvery { repository.sessionDetail(555L) } returnsMany listOf(sessionAt(2), sessionAt(3))
        coEvery { repository.revealCard(555L, 302L) } returns sampleCard(302L, requiredDepth = 3, revealed = true)

        viewModel.load(555L)
        advanceUntilIdle()
        viewModel.revealCard(302L)
        advanceUntilIdle()

        coVerify { repository.revealCard(555L, 302L) }
        assertEquals(3, viewModel.state.value.depthLayer)
    }

    @Test
    fun `submitRetry pass sets result and reloads resolved session`() = runTest {
        coEvery { repository.sessionDetail(555L) } returnsMany listOf(
            sessionAt(depthLayer = 1, status = MineSessionDetail.STATUS_OPEN),
            sessionAt(depthLayer = 1, status = MineSessionDetail.STATUS_RESOLVED),
        )
        coEvery { repository.attempt(555L, any()) } returns MineAttempt(
            cardId = 9L,
            code = "fixed",
            status = MineAttempt.STATUS_PASS,
            message = "通过",
        )

        viewModel.load(555L)
        advanceUntilIdle()
        viewModel.updateRetryCode("print(3.14)")
        viewModel.submitRetry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.lastAttemptPassed)
        assertEquals(MineSessionDetail.STATUS_RESOLVED, state.session?.status)
        assertFalse(state.session?.isOpen ?: true)
    }

    @Test
    fun `submitVariant only fires when depth five with variant`() = runTest {
        coEvery { repository.sessionDetail(555L) } returnsMany listOf(
            sessionAt(depthLayer = 5, withVariant = true),
            sessionAt(depthLayer = 5, withVariant = true, status = MineSessionDetail.STATUS_RESOLVED),
        )
        coEvery { repository.variantAttempt(555L, any()) } returns MineAttempt(
            cardId = 10L,
            code = "v",
            status = MineAttempt.STATUS_PASS,
        )

        viewModel.load(555L)
        advanceUntilIdle()
        viewModel.submitVariant()
        advanceUntilIdle()

        coVerify { repository.variantAttempt(555L, any()) }
        assertTrue(viewModel.state.value.lastAttemptPassed)
    }

    @Test
    fun `submitVariant is a no-op before depth five`() = runTest {
        coEvery { repository.sessionDetail(555L) } returns sessionAt(depthLayer = 3, withVariant = true)

        viewModel.load(555L)
        advanceUntilIdle()
        viewModel.submitVariant()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.variantAttempt(any(), any()) }
    }

    @Test
    fun `close marks session closed`() = runTest {
        coEvery { repository.sessionDetail(555L) } returns sessionAt(depthLayer = 2)
        coEvery { repository.close(555L, MineSessionDetail.STATUS_ABANDONED) } returns
            sessionAt(depthLayer = 2, status = MineSessionDetail.STATUS_ABANDONED)

        viewModel.load(555L)
        advanceUntilIdle()
        viewModel.close(MineSessionDetail.STATUS_ABANDONED)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.closed)
        assertEquals(MineSessionDetail.STATUS_ABANDONED, state.session?.status)
    }

    private fun sessionAt(
        depthLayer: Int,
        status: String = MineSessionDetail.STATUS_OPEN,
        withVariant: Boolean = false,
    ): MineSessionDetail = MineSessionDetail(
        sessionId = 555L,
        status = status,
        submissionId = 9001L,
        problemId = 476L,
        retryCount = 0,
        depthLayer = depthLayer,
        profileDepthTarget = 4,
        masteryConfirmed = false,
        profileGuidance = null,
        variantProblem = if (withVariant) MineVariantProblem(600L, "变式：椭圆面积") else null,
        depthSteps = emptyList(),
        cards = listOf(sampleCard(302L, requiredDepth = 3, revealed = false)),
        problem = null,
    )

    private fun sampleCard(id: Long, requiredDepth: Int, revealed: Boolean): MineSessionCard =
        MineSessionCard(
            id = id,
            cardType = "VARIABLE_TRACE",
            title = "变量轨迹（L3）",
            requiredDepth = requiredDepth,
            revealed = revealed,
            body = if (revealed) "共记录 2 步变量轨迹。" else "",
            expectedOutput = null,
            kcCode = "PY43-KC-016",
        )
}
