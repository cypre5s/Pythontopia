package com.pythontopia.feature.profile

import app.cash.turbine.test
import com.pythontopia.core.common.ApiError
import com.pythontopia.core.common.NetworkError
import com.pythontopia.core.common.Result
import com.pythontopia.feature.profile.data.DimensionUpdateRequest
import com.pythontopia.feature.profile.data.DimensionUpdateResult
import com.pythontopia.feature.profile.data.LearnerProfileDto
import com.pythontopia.feature.profile.data.ProfileDimensions
import com.pythontopia.feature.profile.data.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: ProfileRepository = mockk()
    private lateinit var viewModel: ProfileViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ProfileViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has dashboard tab and no profile and is not loading`() {
        val state = viewModel.state.value
        assertEquals(ProfileTab.DASHBOARD, state.tab)
        assertFalse(state.isLoading)
        assertFalse(state.isSaving)
        assertNull(state.profile)
        assertNull(state.error)
        assertNull(state.savedDimensionKey)
    }

    @Test
    fun `selectTab switches active tab without touching profile`() {
        viewModel.selectTab(ProfileTab.SUBJECTIVE)
        assertEquals(ProfileTab.SUBJECTIVE, viewModel.state.value.tab)
        assertNull(viewModel.state.value.profile)
    }

    @Test
    fun `loadProfile success populates state with profile and clears error`() = runTest {
        val dto = LearnerProfileDto(profileId = 1L, userId = 7L, major = "CS", narrativeSummary = "ok")
        coEvery { repository.fetchProfile() } returns Result.Success(dto)

        viewModel.loadProfile()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("CS", state.profile?.major)
        assertNull(state.error)
        coVerify(exactly = 1) { repository.fetchProfile() }
    }

    @Test
    fun `loadProfile null result is success with no profile`() = runTest {
        coEvery { repository.fetchProfile() } returns Result.Success(null)
        viewModel.loadProfile()
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.profile)
        assertNull(state.error)
    }

    @Test
    fun `loadProfile failure surfaces error and stops loading`() = runTest {
        coEvery { repository.fetchProfile() } returns Result.Failure(
            NetworkError(message = "offline"),
        )

        viewModel.loadProfile()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals("NETWORK_ERROR", state.error?.code)
    }

    @Test
    fun `loadProfile emits intermediate loading=true via state flow`() = runTest {
        // 切到 StandardTestDispatcher 才能观察 isLoading=true 中间帧；
        // UnconfinedTestDispatcher 会把 update 折叠掉。
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scopedViewModel = ProfileViewModel(repository)
        val gate = CompletableDeferred<Result<LearnerProfileDto?>>()
        coEvery { repository.fetchProfile() } coAnswers { gate.await() }

        scopedViewModel.state.test {
            assertFalse(awaitItem().isLoading)
            scopedViewModel.loadProfile() // _state.update isLoading=true 同步发生
            val loading = awaitItem()
            assertTrue(loading.isLoading)

            gate.complete(Result.Success(LearnerProfileDto(major = "CS")))
            advanceUntilIdle()
            val done = awaitItem()
            assertFalse(done.isLoading)
            assertEquals("CS", done.profile?.major)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateSubjective rejects passive dimension without hitting repo`() = runTest {
        viewModel.updateSubjective(
            dimensionKey = ProfileDimensions.KNOWLEDGE_BASE,
            request = DimensionUpdateRequest(scoreDelta = 0.1),
        )
        val state = viewModel.state.value
        assertNotNull(state.error)
        assertEquals("PROFILE_DIMENSION_NOT_SELF_EDITABLE", state.error?.code)
        coVerify(exactly = 0) { repository.updateDimension(any(), any()) }
    }

    @Test
    fun `updateSubjective success sets savedDimensionKey and triggers reload`() = runTest {
        coEvery {
            repository.updateDimension(ProfileDimensions.LEARNING_GOAL, any())
        } returns Result.Success(
            DimensionUpdateResult(
                userId = 1L,
                dimensionKey = ProfileDimensions.LEARNING_GOAL,
                finalDimension = JsonObject(mapOf("score" to JsonPrimitive(0.6))),
            ),
        )
        coEvery { repository.fetchProfile() } returns Result.Success(
            LearnerProfileDto(major = "CS", narrativeSummary = "reloaded"),
        )

        viewModel.updateSubjective(
            dimensionKey = ProfileDimensions.LEARNING_GOAL,
            request = DimensionUpdateRequest(scoreDelta = 0.2, evidenceIncrement = 1),
        )

        val state = viewModel.state.value
        assertFalse(state.isSaving)
        assertEquals(ProfileDimensions.LEARNING_GOAL, state.savedDimensionKey)
        assertEquals("reloaded", state.profile?.narrativeSummary)
        coVerify(exactly = 1) { repository.updateDimension(ProfileDimensions.LEARNING_GOAL, any()) }
        coVerify(exactly = 1) { repository.fetchProfile() }
    }

    @Test
    fun `updateSubjective failure clears saving and surfaces error`() = runTest {
        coEvery {
            repository.updateDimension(any(), any())
        } returns Result.Failure(ApiError(code = "HTTP_500", message = "boom", httpStatus = 500))

        viewModel.updateSubjective(
            dimensionKey = ProfileDimensions.EMOTION_PERSISTENCE,
            request = DimensionUpdateRequest(scoreDelta = -0.1),
        )

        val state = viewModel.state.value
        assertFalse(state.isSaving)
        assertEquals("HTTP_500", state.error?.code)
        assertNull(state.savedDimensionKey)
        coVerify(exactly = 0) { repository.fetchProfile() }
    }

    @Test
    fun `dismissSavedHint and clearError reset transient fields`() = runTest {
        coEvery { repository.updateDimension(any(), any()) } returns Result.Success(
            DimensionUpdateResult(1L, ProfileDimensions.LEARNING_GOAL, JsonObject(emptyMap())),
        )
        coEvery { repository.fetchProfile() } returns Result.Success(null)
        viewModel.updateSubjective(ProfileDimensions.LEARNING_GOAL, DimensionUpdateRequest(scoreDelta = 0.1))
        assertNotNull(viewModel.state.value.savedDimensionKey)
        viewModel.dismissSavedHint()
        assertNull(viewModel.state.value.savedDimensionKey)

        coEvery { repository.fetchProfile() } returns Result.Failure(NetworkError(message = "x"))
        viewModel.loadProfile()
        assertNotNull(viewModel.state.value.error)
        viewModel.clearError()
        assertNull(viewModel.state.value.error)
    }
}
