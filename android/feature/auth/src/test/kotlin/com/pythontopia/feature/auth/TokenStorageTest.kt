package com.pythontopia.feature.auth

import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Android-R4.2 TokenStorage 单元测试。
 *
 * 注：本测试目前不使用 Robolectric。
 *  - 项目级 `testOptions.unitTests.all { it.useJUnitPlatform() }` 强制 JUnit 5 平台，
 *    而 Robolectric 4.13 仅支持 JUnit 4 `RobolectricTestRunner`。
 *    （Robolectric 4.14+ 才提供 `RobolectricExtension`，升级前真机加密层走 instrumented test。）
 *  - 改用 [TokenStorage] 的 `internal` 构造器注入 MockK 化的 [SharedPreferences]：
 *    验证读写契约即可，[EncryptedSharedPreferences] 加密层由 Google 自身的测试覆盖，
 *    我们这里只需要保证 key 一致、缺失字段时退化为 `null`、过期判断逻辑正确。
 */
class TokenStorageTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var storage: TokenStorage

    @BeforeEach
    fun setUp() {
        prefs = mockk(relaxed = false)
        editor = mockk(relaxed = false)
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.clear() } returns editor
        every { editor.apply() } just Runs
        storage = TokenStorage(prefs)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(prefs, editor)
    }

    @Test
    fun `save writes all session fields and refreshToken when provided`() {
        storage.save(
            accessToken = "jwt-abc",
            refreshToken = "ref-xyz",
            userId = 42L,
            username = "alice",
            role = "STUDENT",
            displayName = "Alice",
            expiresAtEpochMs = 1_700_000_000_000L,
        )

        verify(exactly = 1) { editor.putString(TokenStorage.KEY_ACCESS_TOKEN, "jwt-abc") }
        verify(exactly = 1) { editor.putString(TokenStorage.KEY_REFRESH_TOKEN, "ref-xyz") }
        verify(exactly = 1) { editor.putLong(TokenStorage.KEY_USER_ID, 42L) }
        verify(exactly = 1) { editor.putString(TokenStorage.KEY_USERNAME, "alice") }
        verify(exactly = 1) { editor.putString(TokenStorage.KEY_ROLE, "STUDENT") }
        verify(exactly = 1) { editor.putString(TokenStorage.KEY_DISPLAY_NAME, "Alice") }
        verify(exactly = 1) { editor.putLong(TokenStorage.KEY_EXPIRES_AT, 1_700_000_000_000L) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `save removes refreshToken and displayName when null`() {
        storage.save(
            accessToken = "jwt-abc",
            refreshToken = null,
            userId = 42L,
            username = "alice",
            role = "STUDENT",
            displayName = null,
            expiresAtEpochMs = 1_700_000_000_000L,
        )

        verify(exactly = 1) { editor.remove(TokenStorage.KEY_REFRESH_TOKEN) }
        verify(exactly = 1) { editor.remove(TokenStorage.KEY_DISPLAY_NAME) }
        verify(exactly = 0) {
            editor.putString(TokenStorage.KEY_REFRESH_TOKEN, any())
        }
        verify(exactly = 0) {
            editor.putString(TokenStorage.KEY_DISPLAY_NAME, any())
        }
    }

    @Test
    fun `updateAccessToken only rewrites access token and expiry by default`() {
        storage.updateAccessToken(
            accessToken = "jwt-new",
            expiresAtEpochMs = 1_800_000_000_000L,
        )

        verify(exactly = 1) { editor.putString(TokenStorage.KEY_ACCESS_TOKEN, "jwt-new") }
        verify(exactly = 1) { editor.putLong(TokenStorage.KEY_EXPIRES_AT, 1_800_000_000_000L) }
        verify(exactly = 0) { editor.putString(TokenStorage.KEY_USER_ID, any()) }
        verify(exactly = 0) { editor.putString(TokenStorage.KEY_REFRESH_TOKEN, any()) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `updateAccessToken rotates refreshToken when provided`() {
        storage.updateAccessToken(
            accessToken = "jwt-new",
            expiresAtEpochMs = 1_800_000_000_000L,
            refreshToken = "ref-rotated",
        )

        verify(exactly = 1) { editor.putString(TokenStorage.KEY_REFRESH_TOKEN, "ref-rotated") }
    }

    @Test
    fun `readSnapshot returns complete SessionSnapshot when all fields present`() {
        every { prefs.getString(TokenStorage.KEY_ACCESS_TOKEN, null) } returns "jwt-abc"
        every { prefs.getLong(TokenStorage.KEY_USER_ID, any()) } returns 42L
        every { prefs.getString(TokenStorage.KEY_USERNAME, null) } returns "alice"
        every { prefs.getString(TokenStorage.KEY_ROLE, null) } returns "STUDENT"
        every { prefs.getString(TokenStorage.KEY_DISPLAY_NAME, null) } returns "Alice"
        every { prefs.getLong(TokenStorage.KEY_EXPIRES_AT, 0L) } returns 1_700_000_000_000L

        val snapshot = storage.readSnapshot()

        assertNotNull(snapshot)
        assertEquals(42L, snapshot!!.userId)
        assertEquals("alice", snapshot.username)
        assertEquals("STUDENT", snapshot.role)
        assertEquals("Alice", snapshot.displayName)
        assertEquals(1_700_000_000_000L, snapshot.expiresAtEpochMs)
    }

    @Test
    fun `readSnapshot returns null when access token missing`() {
        every { prefs.getString(TokenStorage.KEY_ACCESS_TOKEN, null) } returns null

        val snapshot = storage.readSnapshot()

        assertNull(snapshot)
    }

    @Test
    fun `readSnapshot returns null when access token is blank`() {
        every { prefs.getString(TokenStorage.KEY_ACCESS_TOKEN, null) } returns "   "

        val snapshot = storage.readSnapshot()

        assertNull(snapshot)
    }

    @Test
    fun `readSnapshot returns null when expiresAt is zero (sentinel)`() {
        every { prefs.getString(TokenStorage.KEY_ACCESS_TOKEN, null) } returns "jwt-abc"
        every { prefs.getLong(TokenStorage.KEY_USER_ID, any()) } returns 42L
        every { prefs.getString(TokenStorage.KEY_USERNAME, null) } returns "alice"
        every { prefs.getString(TokenStorage.KEY_ROLE, null) } returns "STUDENT"
        every { prefs.getString(TokenStorage.KEY_DISPLAY_NAME, null) } returns null
        every { prefs.getLong(TokenStorage.KEY_EXPIRES_AT, 0L) } returns 0L

        val snapshot = storage.readSnapshot()

        assertNull(snapshot)
    }

    @Test
    fun `readSnapshot returns null when role missing (corrupted prefs)`() {
        every { prefs.getString(TokenStorage.KEY_ACCESS_TOKEN, null) } returns "jwt-abc"
        every { prefs.getLong(TokenStorage.KEY_USER_ID, any()) } returns 42L
        every { prefs.getString(TokenStorage.KEY_USERNAME, null) } returns "alice"
        every { prefs.getString(TokenStorage.KEY_ROLE, null) } returns null

        val snapshot = storage.readSnapshot()

        assertNull(snapshot)
    }

    @Test
    fun `readAccessToken returns null when no token stored`() {
        every { prefs.getString(TokenStorage.KEY_ACCESS_TOKEN, null) } returns null

        assertNull(storage.readAccessToken())
    }

    @Test
    fun `readRefreshToken returns stored value`() {
        every { prefs.getString(TokenStorage.KEY_REFRESH_TOKEN, null) } returns "ref-xyz"

        assertEquals("ref-xyz", storage.readRefreshToken())
    }

    @Test
    fun `clear forwards to prefs editor clear and apply`() {
        storage.clear()

        verify(exactly = 1) { editor.clear() }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `isExpiringWithin true when token already expired`() {
        val now = 2_000_000_000_000L
        every { prefs.getLong(TokenStorage.KEY_EXPIRES_AT, 0L) } returns now - 1_000L

        assertTrue(storage.isExpiringWithin(thresholdMs = 5 * 60_000L, now = now))
    }

    @Test
    fun `isExpiringWithin true when token expires within threshold`() {
        val now = 2_000_000_000_000L
        every { prefs.getLong(TokenStorage.KEY_EXPIRES_AT, 0L) } returns now + 60_000L

        assertTrue(storage.isExpiringWithin(thresholdMs = 5 * 60_000L, now = now))
    }

    @Test
    fun `isExpiringWithin false when plenty of TTL remaining`() {
        val now = 2_000_000_000_000L
        every { prefs.getLong(TokenStorage.KEY_EXPIRES_AT, 0L) } returns now + 24 * 60 * 60_000L

        assertFalse(storage.isExpiringWithin(thresholdMs = 5 * 60_000L, now = now))
    }

    @Test
    fun `isExpiringWithin true when expiresAt is zero (treat as no session)`() {
        every { prefs.getLong(TokenStorage.KEY_EXPIRES_AT, 0L) } returns 0L

        assertTrue(storage.isExpiringWithin(thresholdMs = 5 * 60_000L, now = 2_000_000_000_000L))
    }

    @Test
    fun `save uses single editor instance and chains all puts before apply`() {
        // 防止以后误改成每次 putXxx 都 `prefs.edit()` 重新拿 editor 导致部分字段写不进去。
        // setUp 已让 `prefs.edit() returns editor`，所有 putXxx 走同一个 editor，
        // 我们只需验证最终 apply() 被调用一次即可。
        storage.save(
            accessToken = "jwt-abc",
            refreshToken = "ref-xyz",
            userId = 1L,
            username = "x",
            role = "STUDENT",
            displayName = "X",
            expiresAtEpochMs = 1L,
        )

        verify(exactly = 1) { editor.apply() }
        // 7 个 putXxx / 0 个 remove（refreshToken 与 displayName 都非空）
        verify(exactly = 5) { editor.putString(any(), any()) }
        verify(exactly = 2) { editor.putLong(any(), any()) }
        verify(exactly = 0) { editor.remove(any()) }
    }
}
