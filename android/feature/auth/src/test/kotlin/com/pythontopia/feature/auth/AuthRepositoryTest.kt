package com.pythontopia.feature.auth

import app.cash.turbine.test
import com.pythontopia.core.common.ApiError
import com.pythontopia.core.common.NetworkError
import com.pythontopia.core.network.AuthTokenHolder
import io.mockk.clearMocks
import io.mockk.coAnswers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Android-R4.1/4.4 AuthRepository 单元测试。
 *
 * 覆盖：
 *  - 登录成功 → 持久化 token + 同步 [AuthTokenHolder] + 返回 [AuthOutcome.Success]
 *  - 登录 401 → 通过 [AuthErrorParser] 翻译成中文，返回 [AuthOutcome.Failure]
 *  - 注册成功 → 同样的 persist 路径
 *  - 刷新无 refresh token → 直接 Failure
 *  - 刷新首次网络抖动 → 自动重试一次成功
 *  - 退出登录 → 即使 API 抛错也清空本地 session
 *  - 通过 Turbine 验证 [SessionSnapshot] 通过 StateFlow 暴露给外部观察者
 *
 * 不依赖 Android runtime / Robolectric：所有 Android API 都通过 MockK 替换。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: FeatureAuthApi
    private lateinit var storage: TokenStorage
    private lateinit var tokenHolder: AuthTokenHolder
    private lateinit var errorParser: AuthErrorParser
    private lateinit var repository: AuthRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        api = mockk(relaxed = false)
        storage = mockk(relaxed = true)
        tokenHolder = mockk(relaxed = true)
        errorParser = mockk()
        repository = AuthRepository(
            api = api,
            tokenStorage = storage,
            tokenHolder = tokenHolder,
            errorParser = errorParser,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearMocks(api, storage, tokenHolder, errorParser)
    }

    @Test
    fun `login success persists tokens, syncs holder and returns Success`() = runTest {
        val loginResponse = LoginResponse(
            token = "jwt-access-1",
            expiresAt = "2099-12-31T23:59:59Z",
            user = AuthUserDto(id = 7L, username = "alice", role = "STUDENT", displayName = "Alice"),
            refreshToken = "jwt-refresh-1",
        )
        coEvery { api.login(any()) } returns loginResponse

        val outcome = repository.login(LoginRequest("alice", "secret123"))

        assertTrue(outcome is AuthOutcome.Success)
        val success = outcome as AuthOutcome.Success
        assertEquals(7L, success.session.userId)
        assertEquals("alice", success.session.username)
        assertEquals("STUDENT", success.session.role)
        assertEquals("Alice", success.session.displayName)
        // expiresAtEpochMs > 0 表示 ISO-8601 解析成功
        assertTrue(success.session.expiresAtEpochMs > 0L)

        coVerify(exactly = 1) { api.login(LoginRequest("alice", "secret123")) }
        verify(exactly = 1) {
            storage.save(
                accessToken = "jwt-access-1",
                refreshToken = "jwt-refresh-1",
                userId = 7L,
                username = "alice",
                role = "STUDENT",
                displayName = "Alice",
                expiresAtEpochMs = any(),
            )
        }
        val tokenSlot = slot<String?>()
        verify(exactly = 1) { tokenHolder.setToken(capture(tokenSlot)) }
        assertEquals("jwt-access-1", tokenSlot.captured)
    }

    @Test
    fun `login HTTP 401 returns Failure with parsed UNAUTHORIZED message`() = runTest {
        val http401 = http(401, """{"code":"UNAUTHORIZED","message":"bad creds"}""")
        coEvery { api.login(any()) } throws http401
        every { errorParser.fromHttp(http401) } returns ApiError(
            code = "UNAUTHORIZED",
            message = "用户名或密码错误，请重新输入",
            httpStatus = 401,
        )

        val outcome = repository.login(LoginRequest("alice", "wrong"))

        assertTrue(outcome is AuthOutcome.Failure)
        val failure = outcome as AuthOutcome.Failure
        assertEquals("UNAUTHORIZED", (failure.error as ApiError).code)
        assertEquals("用户名或密码错误，请重新输入", failure.error.message)
        verify(exactly = 0) { storage.save(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { tokenHolder.setToken(any()) }
    }

    @Test
    fun `login IOException returns NetworkError without persisting`() = runTest {
        coEvery { api.login(any()) } throws IOException("timeout")

        val outcome = repository.login(LoginRequest("alice", "secret123"))

        assertTrue(outcome is AuthOutcome.Failure)
        val failure = outcome as AuthOutcome.Failure
        assertTrue(failure.error is NetworkError)
        assertEquals("timeout", failure.error.message)
        verify(exactly = 0) { storage.save(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `register success persists same as login path`() = runTest {
        val loginResponse = LoginResponse(
            token = "jwt-new",
            expiresAt = "2099-01-01T00:00:00Z",
            user = AuthUserDto(id = 8L, username = "bob", role = "STUDENT", displayName = null),
            refreshToken = null,
        )
        coEvery { api.register(any()) } returns loginResponse

        val outcome = repository.register(RegisterRequest("bob", "Strong1Pass"))

        assertTrue(outcome is AuthOutcome.Success)
        coVerify(exactly = 1) { api.register(RegisterRequest("bob", "Strong1Pass", null)) }
        verify(exactly = 1) {
            storage.save(
                accessToken = "jwt-new",
                refreshToken = null,
                userId = 8L,
                username = "bob",
                role = "STUDENT",
                displayName = null,
                expiresAtEpochMs = any(),
            )
        }
    }

    @Test
    fun `refresh without stored refresh token short-circuits to Failure`() = runTest {
        every { storage.readRefreshToken() } returns null

        val outcome = repository.refresh()

        assertTrue(outcome is AuthOutcome.Failure)
        assertEquals("NO_REFRESH_TOKEN", ((outcome as AuthOutcome.Failure).error as ApiError).code)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun `refresh retries once on IOException and succeeds`() = runTest {
        every { storage.readRefreshToken() } returns "old-refresh"
        val successResponse = LoginResponse(
            token = "jwt-new",
            expiresAt = "2099-12-31T23:59:59Z",
            user = AuthUserDto(id = 7L, username = "alice", role = "STUDENT"),
            refreshToken = "rotated-refresh",
        )
        // 用 coAnswers 显式控制每次调用的行为：第 1 次抛 IOException，第 2 次返回成功。
        // 比 `throws X andThen Y` 链式语法更直观，避免 coEvery 与 andThen 推断歧义。
        var callCount = 0
        coEvery { api.refresh(any()) } coAnswers {
            callCount++
            if (callCount == 1) throw IOException("flaky") else successResponse
        }

        val outcome = repository.refresh(autoRetryOnce = true)

        assertTrue(outcome is AuthOutcome.Success)
        coVerify(exactly = 2) { api.refresh(RefreshRequest("old-refresh")) }
        assertEquals(2, callCount)
    }

    @Test
    fun `refresh does not retry on HTTP error (only network)`() = runTest {
        every { storage.readRefreshToken() } returns "old-refresh"
        val http401 = http(401, """{"code":"AUTH_REFRESH_INVALID","message":"x"}""")
        coEvery { api.refresh(any()) } throws http401
        every { errorParser.fromHttp(http401) } returns ApiError(
            code = "AUTH_REFRESH_INVALID",
            message = "登录已失效，请重新输入账号密码",
            httpStatus = 401,
        )

        val outcome = repository.refresh(autoRetryOnce = true)

        assertTrue(outcome is AuthOutcome.Failure)
        // Only 1 call, no retry — HTTP errors are deterministic
        coVerify(exactly = 1) { api.refresh(RefreshRequest("old-refresh")) }
    }

    @Test
    fun `logout clears local session even when API throws`() = runTest {
        every { storage.readRefreshToken() } returns "refresh-x"
        coEvery { api.logout(any()) } throws IOException("server down")

        repository.logout()

        verify(exactly = 1) { storage.clear() }
        verify(exactly = 1) { tokenHolder.setToken(null) }
    }

    @Test
    fun `restoreFromDisk repopulates tokenHolder from existing snapshot`() {
        val snapshot = SessionSnapshot(
            userId = 7L,
            username = "alice",
            role = "STUDENT",
            displayName = "Alice",
            expiresAtEpochMs = 1_700_000_000_000L,
        )
        every { storage.readSnapshot() } returns snapshot
        every { storage.readAccessToken() } returns "disk-jwt"

        val restored = repository.restoreFromDisk()

        assertNotNull(restored)
        assertEquals(7L, restored?.userId)
        verify(exactly = 1) { tokenHolder.setToken("disk-jwt") }
    }

    @Test
    fun `restoreFromDisk returns null when no snapshot present`() {
        every { storage.readSnapshot() } returns null

        val restored = repository.restoreFromDisk()

        assertNull(restored)
        verify(exactly = 0) { tokenHolder.setToken(any()) }
    }

    @Test
    fun `consumer observing repository outcome via external StateFlow sees Success exactly once`() = runTest {
        // 验证 ViewModel/调用方拿到 outcome 后通过 StateFlow 暴露给 UI 的链路：
        // 不直接对 repository 加 Flow 接口，而是模拟 ViewModel 拿到 outcome 后 update 一次。
        val loginResponse = LoginResponse(
            token = "jwt-flow",
            expiresAt = "2099-12-31T23:59:59Z",
            user = AuthUserDto(id = 1L, username = "alice", role = "STUDENT"),
        )
        coEvery { api.login(any()) } returns loginResponse

        val observed = MutableStateFlow<AuthOutcome?>(null)

        observed.test {
            assertNull(awaitItem())
            observed.value = repository.login(LoginRequest("alice", "secret123"))
            val outcome = awaitItem()
            assertTrue(outcome is AuthOutcome.Success)
        }
    }

    private companion object {
        fun http(code: Int, jsonBody: String): HttpException {
            val body = jsonBody.toResponseBody("application/json".toMediaType())
            return HttpException(Response.error<Any>(code, body))
        }
    }
}
