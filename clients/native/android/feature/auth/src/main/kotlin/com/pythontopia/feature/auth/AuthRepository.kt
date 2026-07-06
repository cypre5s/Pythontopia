package com.pythontopia.feature.auth

import com.pythontopia.core.common.ApiError
import com.pythontopia.core.common.NetworkError
import com.pythontopia.core.common.UnknownError
import com.pythontopia.core.network.AuthTokenHolder
import com.pythontopia.core.network.SessionState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Android-R4.1/4.4 认证仓库。
 *
 * 单一职责：把 4 个 `/api/auth/{endpoint}` 端点翻译成 [AuthOutcome]，并同步两侧 token：
 *  - 落盘到 [TokenStorage]（重启后能恢复）。
 *  - 写入 [AuthTokenHolder]（`:core:network` AuthInterceptor 真正读的位置）。
 *
 * 错误处理统一通过 [runCatchingApi] + [AuthErrorParser]：
 *  - HTTP 异常走 4001-4099 中文表（[AuthErrorMap]）。
 *  - IOException 归 [NetworkError]，反序列化失败归 [UnknownError]。
 *
 * Refresh 重试策略：[refresh] 仅在网络抖动（[NetworkError]）时自动重试一次；
 * HTTP 4xx/5xx 视为确定性失败直接返回，避免对后端造成无效压力。
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: FeatureAuthApi,
    private val tokenStorage: TokenStorage,
    private val tokenHolder: AuthTokenHolder,
    private val errorParser: AuthErrorParser = AuthErrorParser.Default,
    private val sessionState: SessionState = SessionState(),
) {

    suspend fun login(request: LoginRequest): AuthOutcome =
        runCatchingApi {
            val response = api.login(request)
            persist(response)
        }

    suspend fun register(request: RegisterRequest): AuthOutcome =
        runCatchingApi {
            val user = api.register(request)
            AuthOutcome.Success(
                session = SessionSnapshot(
                    userId = user.id,
                    username = user.username,
                    role = user.role,
                    displayName = user.displayName,
                    expiresAtEpochMs = 0L,
                ),
            )
        }

    /**
     * 用持久化的 refresh token 续签 access token。
     *
     * R-13 后端落地之前，`/api/auth/refresh` 返回 501，本方法会回到 [AuthOutcome.Failure]
     * 并把错误码 `NOT_IMPLEMENTED` 翻成中文提示，UI 直接走 Login 流即可。
     *
     * `autoRetryOnce = true` 时，若首次刷新因网络抖动失败，会再试一次（仅一次）。
     */
    suspend fun refresh(autoRetryOnce: Boolean = true): AuthOutcome {
        val refreshToken = tokenStorage.readRefreshToken()
            ?: return AuthOutcome.Failure(
                ApiError(code = "NO_REFRESH_TOKEN", message = "登录已过期，请重新输入账号密码"),
            )
        val first = runCatchingApi { persist(api.refresh(RefreshRequest(refreshToken))) }
        if (first is AuthOutcome.Success || !autoRetryOnce) return first
        if (!shouldRetry(first)) return first
        return runCatchingApi { persist(api.refresh(RefreshRequest(refreshToken))) }
    }

    /**
     * 通知后端撤销 refresh，然后清空本地 token。
     * 服务端调用失败也仍然清本地——客户端的"我已退出"状态必须立即生效。
     */
    suspend fun logout() {
        clearSession()
    }

    /** 启动时调用：盘上 token 还在且没过期，则恢复到内存桥 [AuthTokenHolder]。 */
    fun restoreFromDisk(): SessionSnapshot? {
        val snapshot = tokenStorage.readSnapshot() ?: return null
        val accessToken = tokenStorage.readAccessToken() ?: return null
        tokenHolder.setToken(accessToken)
        return snapshot
    }

    /**
     * 启动自动登录：盘上 token 存在**且未过期**才恢复到内存桥并返回会话；
     * 已过期则清空（避免拿着死 token 反复 401）。磁盘 + Keystore I/O，调用方须放 IO 线程。
     */
    fun restoreValidSession(): SessionSnapshot? {
        val snapshot = tokenStorage.readSnapshot() ?: return null
        if (tokenStorage.isExpiringWithin(0L)) {
            clearSession()
            return null
        }
        val accessToken = tokenStorage.readAccessToken() ?: return null
        tokenHolder.setToken(accessToken)
        sessionState.isGuest = false
        return snapshot
    }

    fun clearSession() {
        tokenHolder.setToken(null)
        tokenStorage.clear()
        sessionState.isGuest = false
    }

    private fun persist(response: LoginResponse): AuthOutcome.Success {
        val expiresAtEpochMs = parseExpiresAt(response.expiresAt)
        tokenStorage.save(
            accessToken = response.token,
            refreshToken = response.refreshToken,
            userId = response.user.id,
            username = response.user.username,
            role = response.user.role,
            displayName = response.user.displayName,
            expiresAtEpochMs = expiresAtEpochMs,
        )
        tokenHolder.setToken(response.token)
        sessionState.isGuest = false
        return AuthOutcome.Success(
            session = SessionSnapshot(
                userId = response.user.id,
                username = response.user.username,
                role = response.user.role,
                displayName = response.user.displayName,
                expiresAtEpochMs = expiresAtEpochMs,
            ),
        )
    }

    private fun shouldRetry(outcome: AuthOutcome): Boolean {
        if (outcome !is AuthOutcome.Failure) return false
        return outcome.error is NetworkError
    }

    private suspend fun runCatchingApi(block: suspend () -> AuthOutcome.Success): AuthOutcome =
        try {
            block()
        } catch (httpEx: HttpException) {
            AuthOutcome.Failure(errorParser.fromHttp(httpEx))
        } catch (ioEx: IOException) {
            AuthOutcome.Failure(NetworkError(message = ioEx.message ?: "网络连接失败，请检查网络后重试"))
        } catch (serEx: SerializationException) {
            AuthOutcome.Failure(UnknownError(message = "服务端响应格式异常：${serEx.message ?: "未知"}"))
        }

    private companion object {
        /**
         * 后端返回 ISO-8601（如 `2026-06-01T12:34:56Z`）或纯 epoch 毫秒（向后兼容）。
         * 解析失败时退化为 `now + 1h`，让客户端立即触发 toast 提示而不是 NPE。
         */
        fun parseExpiresAt(raw: String): Long {
            raw.toLongOrNull()?.let { return it }
            return try {
                Instant.parse(raw).toEpochMilli()
            } catch (_: DateTimeParseException) {
                System.currentTimeMillis() + DEFAULT_TTL_MS
            } catch (_: IllegalArgumentException) {
                System.currentTimeMillis() + DEFAULT_TTL_MS
            }
        }

        const val DEFAULT_TTL_MS: Long = 60L * 60L * 1000L
    }
}

/**
 * 仅 feature 内可见的 Retrofit 接口；
 * 直接放在 feature 模块，避免把仍在迭代的 refresh / logout 契约提前固化到 :core:network。
 *
 * login + register 走同一 4 端点表，目的是让 AuthRepository 一处持有完整契约，
 * 不和 `:core:network/api/AuthApi`（legacy / Web 端共用版）形成两条并行调用链。
 */
interface FeatureAuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body req: RegisterRequest): AuthUserDto

    @POST("api/auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): LoginResponse

}

/**
 * 把 [HttpException] 翻译成 [ApiError]。提取成接口是为了在 [AuthRepositoryTest] 里
 * 用一个 in-memory 实现替换，免去构造真实 Retrofit `Response` 的成本。
 */
interface AuthErrorParser {
    fun fromHttp(ex: HttpException): ApiError

    object Default : AuthErrorParser {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        override fun fromHttp(ex: HttpException): ApiError {
            val status = ex.code()
            val errorBody = runCatching { ex.response()?.errorBody()?.string().orEmpty() }
                .getOrDefault("")
            val parsed = errorBody.takeIf { it.isNotBlank() }?.let { body ->
                runCatching { json.decodeFromString(AuthApiErrorDto.serializer(), body) }.getOrNull()
            }
            val code = parsed?.code ?: defaultCodeFor(status)
            val message = AuthErrorMap.resolve(code = code, numericCode = parsed?.numericCode, httpStatus = status)
            return ApiError(code = code, message = message, httpStatus = status)
        }

        private fun defaultCodeFor(status: Int): String = when (status) {
            HTTP_UNAUTHORIZED -> "UNAUTHORIZED"
            HTTP_FORBIDDEN -> "FORBIDDEN"
            HTTP_NOT_FOUND -> "NOT_FOUND"
            HTTP_CONFLICT -> "CONFLICT"
            HTTP_BAD_REQUEST -> "VALIDATION_ERROR"
            HTTP_NOT_IMPLEMENTED -> "NOT_IMPLEMENTED"
            in HTTP_INTERNAL_RANGE -> "INTERNAL_ERROR"
            else -> "UNKNOWN"
        }

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_NOT_IMPLEMENTED = 501
        private val HTTP_INTERNAL_RANGE = 500..599
    }
}

/**
 * 4001-4099 错误码 → 中文文案。
 * 数据来自 `docs/clients/android/02-shared-state-contract.md` §9.2，按 auth 高频场景裁剪。
 *
 * `numericCode` 在 R-12 后端补齐前可能缺失，所以以字符串 `code` 为主匹配键，
 * 数字码作为辅助（避免不同后端版本下文案错位）。
 */
internal object AuthErrorMap {
    private val BY_CODE: Map<String, String> = mapOf(
        "VALIDATION_ERROR" to "输入内容不符合要求", // 4001
        "UNAUTHORIZED" to "用户名或密码错误，请重新输入", // 4002
        "FORBIDDEN" to "没有权限执行该操作", // 4003
        "NOT_FOUND" to "请求的资源不存在", // 4004
        "CONFLICT" to "操作与当前数据状态冲突", // 4005
        "RATE_LIMITED" to "操作过于频繁，请稍后再试", // 4006
        "AUTH_USERNAME_TAKEN" to "用户名已被占用", // 4011（auth 自定义）
        "AUTH_PASSWORD_WEAK" to "密码强度不足，至少 8 位且包含字母与数字", // 4012
        "AUTH_USER_DISABLED" to "账号已被停用，请联系管理员", // 4013
        "AUTH_TOKEN_EXPIRED" to "登录已过期，请重新登录", // 4014
        "AUTH_REFRESH_INVALID" to "登录已失效，请重新输入账号密码", // 4015
        "NO_REFRESH_TOKEN" to "登录已过期，请重新输入账号密码", // 客户端自造
        "NOT_IMPLEMENTED" to "该功能后端尚未上线，请稍后再试", // 501
        "INTERNAL_ERROR" to "服务器开小差了，请稍后再试", // 5001
        "UNKNOWN" to "未知错误，请稍后再试",
    )

    fun resolve(code: String?, numericCode: Int?, httpStatus: Int?): String {
        val byCode = code?.let { BY_CODE[it] }
        if (byCode != null) return byCode
        return when {
            numericCode != null -> "请求失败（错误码 $numericCode）"
            httpStatus != null -> "请求失败（HTTP $httpStatus）"
            else -> BY_CODE.getValue("UNKNOWN")
        }
    }
}

/**
 * Feature 内 Hilt 模块：把 [FeatureAuthApi] 与默认 [AuthErrorParser] 注入到容器。
 *
 * Retrofit 实例由 `:core:network/NetworkModule.provideRetrofit` 提供，本模块直接复用，
 * 避免在 feature 层重复配置 base URL / 拦截器 / JSON 序列化策略。
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AuthFeatureModule {

    @Provides
    @Singleton
    fun provideFeatureAuthApi(retrofit: Retrofit): FeatureAuthApi =
        retrofit.create(FeatureAuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthErrorParser(): AuthErrorParser = AuthErrorParser.Default
}
