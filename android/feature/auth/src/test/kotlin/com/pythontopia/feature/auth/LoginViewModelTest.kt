package com.pythontopia.feature.auth

import app.cash.turbine.test
import com.pythontopia.core.common.ApiError
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Android-R4.1 LoginViewModel 真实仓库改造后的单元测试。
 *
 * 与原版本兼容性：保留 `onUsernameChange ...` 用例（仅测 state 更新，不依赖仓库），
 * 把原"500ms stub delay"路径替换成"mocked AuthRepository.login 路径"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AuthRepository
    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = LoginViewModel(repository)
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
    fun `login resolves to success when repository succeeds`() = runTest {
        val expectedSession = SessionSnapshot(
            userId = 42L,
            username = "alice",
            role = "STUDENT",
            displayName = "Alice",
            expiresAtEpochMs = 9_999_999_999L,
        )
        coEvery { repository.login(any()) } returns AuthOutcome.Success(expectedSession)

        viewModel.onUsernameChange("alice")
        viewModel.onPasswordChange("secret123")

        viewModel.state.test {
            val initial = awaitItem()
            assertEquals("alice", initial.username)
            assertEquals("secret123", initial.password)
            assertFalse(initial.success)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.login()
        advanceUntilIdle()

        val done = viewModel.state.value
        assertFalse(done.isLoading)
        assertTrue(done.success)
        assertNull(done.errorMessage)
        assertNotNull(done.lastSession)
        assertEquals(42L, done.lastSession?.userId)
    }

    @Test
    fun `login surfaces repository failure message into errorMessage`() = runTest {
        coEvery { repository.login(any()) } returns AuthOutcome.Failure(
            ApiError(code = "UNAUTHORIZED", message = "用户名或密码错误，请重新输入", httpStatus = 401),
        )

        viewModel.onUsernameChange("alice")
        viewModel.onPasswordChange("wrong")

        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.success)
        assertEquals("用户名或密码错误，请重新输入", state.errorMessage)
        assertNull(state.lastSession)
    }

    @Test
    fun `login short-circuits to local error when username or password is blank`() = runTest {
        viewModel.onUsernameChange("")
        viewModel.onPasswordChange("")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.success)
        assertEquals("请输入用户名和密码", state.errorMessage)
    }
}
