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
class MineViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: MineRepository = mockk(relaxed = false)
    private lateinit var viewModel: MineViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MineViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has isLoading false`() {
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.session)
        assertEquals(0, viewModel.state.value.cardIdx)
    }

    @Test
    fun `startSession loads session and primes editor with first card snippet`() = runTest {
        coEvery { repository.startSession(null) } returns sampleSession(1L)
        coEvery { repository.cards(1L) } returns listOf(sampleCard(101L, layer = 1), sampleCard(102L, layer = 1))

        viewModel.startSession(srcSubmissionId = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.session)
        assertEquals(2, state.cards.size)
        assertEquals(0, state.cardIdx)
        assertEquals(sampleCard(101L, layer = 1).snippet, state.userCode)
    }

    @Test
    fun `nextCard advances within local cards list and resets attempt artifacts`() = runTest {
        givenSessionWithCards(
            sessionId = 1L,
            cards = listOf(sampleCard(101L, layer = 1), sampleCard(102L, layer = 2)),
        )
        viewModel.startSession()
        advanceUntilIdle()
        viewModel.updateCode("# my edits in card 1")

        viewModel.nextCard()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.cardIdx)
        assertEquals(102L, state.currentCard?.id)
        assertEquals(2, state.currentLayer)
        assertEquals(sampleCard(102L, layer = 2).snippet, state.userCode)
        assertEquals(0, state.failureCount)
        assertNull(state.lastResult)
        assertFalse(state.showEscapeDialog)
    }

    @Test
    fun `nextCard at end of list calls repository next and marks completed when null`() = runTest {
        givenSessionWithCards(sessionId = 1L, cards = listOf(sampleCard(101L, layer = 1)))
        coEvery { repository.next(1L) } returns null

        viewModel.startSession()
        advanceUntilIdle()
        viewModel.nextCard()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isCompleted)
        assertFalse(state.isLoading)
        coVerify { repository.next(1L) }
    }

    @Test
    fun `nextCard at end of list appends new card when repository returns one`() = runTest {
        givenSessionWithCards(sessionId = 1L, cards = listOf(sampleCard(101L, layer = 1)))
        val extra = sampleCard(103L, layer = 2, deckCardId = "B2-01", title = "站门口的 if")
        coEvery { repository.next(1L) } returns extra

        viewModel.startSession()
        advanceUntilIdle()
        viewModel.nextCard()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isCompleted)
        assertEquals(2, state.cards.size)
        assertEquals(extra.id, state.currentCard?.id)
        assertEquals(extra.snippet, state.userCode)
    }

    @Test
    fun `submit pass clears failureCount and exposes pass result`() = runTest {
        givenSessionWithCards(sessionId = 1L, cards = listOf(sampleCard(101L, layer = 1)))
        coEvery { repository.attempt(1L, any()) } returns MineAttempt(
            cardId = 101L,
            code = "fixed",
            status = MineAttempt.STATUS_PASS,
            actualOutput = "spell\n",
            message = "通过",
        )

        viewModel.startSession()
        advanceUntilIdle()
        viewModel.updateCode("magick = 'spell'\nprint(magick)")
        viewModel.submitAttempt()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(MineAttempt.STATUS_PASS, state.lastResult?.status)
        assertEquals(0, state.failureCount)
        assertFalse(state.showEscapeDialog)
        assertTrue(state.lastAttemptPassed)
    }

    @Test
    fun `submit fail increments failureCount but does not show dialog before threshold`() = runTest {
        givenSessionWithCards(sessionId = 1L, cards = listOf(sampleCard(101L, layer = 1)))
        coEvery { repository.attempt(any(), any()) } returns MineAttempt(
            cardId = 101L,
            code = "bad",
            status = MineAttempt.STATUS_FAIL,
            message = "样例 1 输出不一致",
        )

        viewModel.startSession()
        advanceUntilIdle()
        viewModel.submitAttempt()
        advanceUntilIdle()
        viewModel.submitAttempt()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.failureCount)
        assertFalse(state.showEscapeDialog)
        assertEquals(MineAttempt.STATUS_FAIL, state.lastResult?.status)
    }

    @Test
    fun `third consecutive failure triggers escape dialog`() = runTest {
        givenSessionWithCards(sessionId = 1L, cards = listOf(sampleCard(101L, layer = 1)))
        coEvery { repository.attempt(any(), any()) } returns MineAttempt(
            cardId = 101L,
            code = "bad",
            status = MineAttempt.STATUS_FAIL,
        )

        viewModel.startSession()
        advanceUntilIdle()
        repeat(MineUiState.ESCAPE_THRESHOLD) {
            viewModel.submitAttempt()
            advanceUntilIdle()
        }

        val state = viewModel.state.value
        assertEquals(MineUiState.ESCAPE_THRESHOLD, state.failureCount)
        assertTrue(state.showEscapeDialog)
    }

    @Test
    fun `escapeCurrentCard closes dialog and advances to next card`() = runTest {
        givenSessionWithCards(
            sessionId = 1L,
            cards = listOf(sampleCard(101L, layer = 1), sampleCard(102L, layer = 1)),
        )
        coEvery { repository.attempt(any(), any()) } returns MineAttempt(
            cardId = 101L,
            code = "bad",
            status = MineAttempt.STATUS_FAIL,
        )

        viewModel.startSession()
        advanceUntilIdle()
        repeat(MineUiState.ESCAPE_THRESHOLD) {
            viewModel.submitAttempt()
            advanceUntilIdle()
        }
        assertTrue(viewModel.state.value.showEscapeDialog)

        viewModel.escapeCurrentCard()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.showEscapeDialog)
        assertEquals(1, state.cardIdx)
        assertEquals(102L, state.currentCard?.id)
        assertEquals(0, state.failureCount)
        assertNull(state.lastResult)
    }

    @Test
    fun `summonNpcHint closes dialog and marks hint revealed without skipping card`() = runTest {
        givenSessionWithCards(
            sessionId = 1L,
            cards = listOf(sampleCard(101L, layer = 1), sampleCard(102L, layer = 1)),
        )
        coEvery { repository.attempt(any(), any()) } returns MineAttempt(
            cardId = 101L,
            code = "bad",
            status = MineAttempt.STATUS_FAIL,
        )

        viewModel.startSession()
        advanceUntilIdle()
        repeat(MineUiState.ESCAPE_THRESHOLD) {
            viewModel.submitAttempt()
            advanceUntilIdle()
        }
        viewModel.summonNpcHint()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.showEscapeDialog)
        assertTrue(state.npcHintRevealed)
        assertEquals(0, state.cardIdx)
        assertEquals(101L, state.currentCard?.id)
        assertEquals(MineUiState.ESCAPE_THRESHOLD, state.failureCount)
    }

    @Test
    fun `dismissEscapeDialog keeps failure count so it cannot be reset by repeated dismiss`() = runTest {
        givenSessionWithCards(sessionId = 1L, cards = listOf(sampleCard(101L, layer = 1)))
        coEvery { repository.attempt(any(), any()) } returns MineAttempt(
            cardId = 101L,
            code = "bad",
            status = MineAttempt.STATUS_FAIL,
        )

        viewModel.startSession()
        advanceUntilIdle()
        repeat(MineUiState.ESCAPE_THRESHOLD) {
            viewModel.submitAttempt()
            advanceUntilIdle()
        }
        viewModel.dismissEscapeDialog()

        val state = viewModel.state.value
        assertFalse(state.showEscapeDialog)
        assertEquals(MineUiState.ESCAPE_THRESHOLD, state.failureCount)
    }

    @Test
    fun `startSession surfaces repository error in errorMessage`() = runTest {
        coEvery { repository.startSession(any()) } throws RuntimeException("offline")

        viewModel.startSession(srcSubmissionId = 42L)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.session)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("offline"))
    }

    private fun givenSessionWithCards(sessionId: Long, cards: List<MineCard>) {
        coEvery { repository.startSession(any()) } returns sampleSession(sessionId)
        coEvery { repository.cards(sessionId) } returns cards
    }

    private fun sampleSession(sessionId: Long): MineSession = MineSession(
        sessionId = sessionId,
        entry = MineSession.ENTRY_FAILED_SUBMISSION,
        sourceSubmissionId = 9981L,
        currentLayer = 1,
        totalCards = 5,
    )

    private fun sampleCard(
        id: Long,
        layer: Int,
        deckCardId: String = "B$layer-0${(id % 10).coerceAtLeast(1)}",
        title: String = "测试卡 $id",
    ): MineCard = MineCard(
        id = id,
        deckCardId = deckCardId,
        layer = layer,
        title = title,
        snippet = "print(missing_var_$id)",
        expectedOutput = "ok\n",
        kcCode = "PY43-KC-010",
        pythocoReward = 5,
    )
}
