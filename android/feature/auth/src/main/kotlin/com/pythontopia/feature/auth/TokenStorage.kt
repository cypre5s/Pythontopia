package com.pythontopia.feature.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-R4.2 JWT 持久化层。
 *
 * 与 `docs/android/02-shared-state-contract.md` §8.1/8.2 对齐：
 *  - 使用 `MasterKey.KeyScheme.AES256_GCM` 派生主密钥（落在 Android Keystore），
 *  - 通过 [EncryptedSharedPreferences] 以 AES-256-SIV (key) + AES-256-GCM (value) 加密落盘。
 *
 * 设计要点：
 *  - 提供 `internal` 构造器接收任意 [SharedPreferences]，让 [TokenStorageTest] 用 MockK
 *    替换底层存储，避免单测引入 Robolectric / Keystore 真实依赖
 *    （Robolectric 4.13 + JUnit 5 平台目前不互通，等 4.14 升级后再补真机加密测试）。
 *  - [readSnapshot] 在任一关键字段缺失时返回 `null`，让上层用同一个 "未登录" 路径处理。
 *  - [isExpiringWithin] 用 epoch 毫秒比较，规避 ISO-8601 解析误差。
 */
@Singleton
class TokenStorage @VisibleForTesting constructor(
    private val prefs: SharedPreferences,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(createEncryptedPrefs(context))

    /**
     * 一次性写入完整 session，避免分段失败导致的字段错位。
     * `refreshToken` 在后端 R-13 上线前可能为 `null`，允许传 `null` 表示不持有 refresh。
     */
    fun save(
        accessToken: String,
        refreshToken: String?,
        userId: Long,
        username: String,
        role: String,
        displayName: String?,
        expiresAtEpochMs: Long,
    ) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken == null) {
                remove(KEY_REFRESH_TOKEN)
            } else {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            putLong(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putString(KEY_ROLE, role)
            if (displayName == null) {
                remove(KEY_DISPLAY_NAME)
            } else {
                putString(KEY_DISPLAY_NAME, displayName)
            }
            putLong(KEY_EXPIRES_AT, expiresAtEpochMs)
        }.apply()
    }

    /** 仅刷新 access token 与过期时间，保留其它身份字段；用于 [AuthRepository.refresh]。 */
    fun updateAccessToken(accessToken: String, expiresAtEpochMs: Long, refreshToken: String? = null) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putLong(KEY_EXPIRES_AT, expiresAtEpochMs)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
        }.apply()
    }

    fun readAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun readRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    /**
     * 读取完整 session；任一关键字段（token / userId / username / role / expiresAt）缺失即返回 `null`。
     * 上层据此判断是否需要走 Login 流程。
     */
    fun readSnapshot(): SessionSnapshot? {
        val fields = readRequiredSessionFields() ?: return null
        return SessionSnapshot(
            userId = fields.userId,
            username = fields.username,
            role = fields.role,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null),
            expiresAtEpochMs = fields.expiresAtEpochMs,
        )
    }

    private fun readRequiredSessionFields(): RequiredSessionFields? {
        val accessToken = readAccessToken()?.takeUnless { it.isBlank() }
        val userId = accessToken?.let {
            prefs.getLong(KEY_USER_ID, INVALID_USER_ID)
                .takeIf { storedUserId -> storedUserId != INVALID_USER_ID }
        }
        val username = userId?.let {
            prefs.getString(KEY_USERNAME, null)?.takeUnless { storedUsername -> storedUsername.isBlank() }
        }
        val role = username?.let {
            prefs.getString(KEY_ROLE, null)?.takeUnless { storedRole -> storedRole.isBlank() }
    }
    val expiresAt = role?.let {
        prefs.getLong(KEY_EXPIRES_AT, 0L).takeIf { storedExpiresAt -> storedExpiresAt != 0L }
    }
    return expiresAt?.let { requiredExpiresAt ->
        RequiredSessionFields(
            userId = requireNotNull(userId),
            username = requireNotNull(username),
            role = requireNotNull(role),
            expiresAtEpochMs = requiredExpiresAt,
        )
    }
}

    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * 判断 token 是否会在 [thresholdMs] 内过期。用于：
     *  - `< 0` → 已过期，必须重新登录或 refresh
     *  - `< 5 * 60_000` → 过期前 5 分钟，UI 弹 toast 提示（契约 §8.1）
     *
     * @param now 当前时间（默认 `System.currentTimeMillis()`），测试可注入固定时间。
     */
    fun isExpiringWithin(thresholdMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt == 0L) return true
        return expiresAt - now <= thresholdMs
    }

    internal companion object {
        const val PREF_FILE_NAME: String = "pythontopia_auth_secure"
        const val KEY_ACCESS_TOKEN: String = "access_token"
        const val KEY_REFRESH_TOKEN: String = "refresh_token"
        const val KEY_USER_ID: String = "user_id"
        const val KEY_USERNAME: String = "username"
        const val KEY_ROLE: String = "role"
        const val KEY_DISPLAY_NAME: String = "display_name"
        const val KEY_EXPIRES_AT: String = "expires_at_epoch_ms"
        private const val INVALID_USER_ID: Long = -1L
    }
}

private data class RequiredSessionFields(
    val userId: Long,
    val username: String,
    val role: String,
    val expiresAtEpochMs: Long,
)

private fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        TokenStorage.PREF_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
