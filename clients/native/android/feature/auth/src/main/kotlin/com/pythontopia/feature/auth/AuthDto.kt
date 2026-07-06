package com.pythontopia.feature.auth

import com.pythontopia.core.common.AppError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Android-R4.1 feature-local 认证契约。
 *
 * 与 `:core:data/dto` 已有的 `LoginRequest`/`LoginResponse` 同形，但保留在 feature 内：
 *  - 后端 `/api/auth/refresh` 与 `/api/auth/logout` 仍在落地中（ADR-001 §1 决策 13），
 *    R-12 不希望被 `:core:network` 的稳定契约绑定，先在 feature 内迭代。
 *  - 共享状态契约 `docs/clients/android/02-shared-state-contract.md` §8.2 约定 `expiresAt` 为
 *    ISO-8601 字符串，由 [AuthRepository] 解析后写入 [TokenStorage]。
 */

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String,
    val role: String = "STUDENT",
    val email: String? = null,
    val teacherInviteCode: String? = null,
)

@Serializable
data class RefreshRequest(
    @SerialName("token")
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String? = null,
)

@Serializable
data class AuthUserDto(
    val id: Long,
    val username: String,
    val role: String,
    val displayName: String? = null,
)

/**
 * 后端 `LoginResponse` 当前只返回 `token + expiresAt + user`；R-13 起会增加
 * `refreshToken`，为了让 [AuthRepository.refresh] 一上线就能消费，先把字段声明为可选。
 *
 * `expiresAt` 是 ISO-8601 UTC 字符串（如 `2026-06-01T12:34:56Z`），写盘前由
 * [AuthRepository] 内部 parseExpiresAt 归一化成 epoch 毫秒。
 */
@Serializable
data class LoginResponse(
    val token: String,
    val expiresAt: String,
    val user: AuthUserDto,
    val refreshToken: String? = null,
)

/**
 * 后端 4001-4099 错误码契约，参考 `docs/clients/android/02-shared-state-contract.md` §9.2。
 * 仅声明字符串 `code`，因为后端 `numericCode` 仍在分阶段补齐。
 */
@Serializable
data class AuthApiErrorDto(
    val code: String,
    val message: String,
    @SerialName("numericCode") val numericCode: Int? = null,
)

/**
 * Repository 与 ViewModel 之间的统一回执：避免 `try/catch` 跨层抛 `HttpException`。
 *
 * - [Success] 携带刚持久化好的 session 摘要，调用方可直接路由到 Hub。
 * - [Failure] 携带 `AppError`，UI 层用 `error.message` 渲染红字提示即可。
 */
sealed interface AuthOutcome {
    data class Success(val session: SessionSnapshot) : AuthOutcome
    data class Failure(val error: AppError) : AuthOutcome
}

/**
 * 与 [TokenStorage.readSnapshot] 返回类型一致：表达"现在登录的人是谁、token 何时过期"。
 *
 * 不持有原始 password / refreshToken：UI 不该看到任何 secret material，
 * 只有 [AuthRepository] 与 [TokenStorage] 互相传递时才会出现这两个字段。
 */
data class SessionSnapshot(
    val userId: Long,
    val username: String,
    val role: String,
    val displayName: String?,
    val expiresAtEpochMs: Long,
)
