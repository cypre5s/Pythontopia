package com.pythontopia.feature.profile.data

import com.pythontopia.core.common.ApiError
import com.pythontopia.core.common.AppError
import com.pythontopia.core.common.NetworkError
import com.pythontopia.core.common.Result
import com.pythontopia.core.common.UnknownError
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * 包装 [ProfileApi]，把 throwable 收敛成 [Result] 让 ViewModel 走单一 sealed 分支。
 *
 * 与 `DialogRepository` 的 "fail-silent + 本地 fallback" 不同：profile 是教育核心
 * （多智能体输出 + 防幻觉），失败要 surface 给用户，不允许静默降级。
 * 这里只做错误归一，不做 fallback 数据。
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val api: ProfileApi,
) {

    /**
     * GET `/api/learner/profile`。
     *
     * - 200 → `Success(LearnerProfileDto)`
     * - 204 → `Success(null)`（学生未完成访谈，画像未生成；UI 引导去访谈）
     * - 4xx/5xx → `Failure(ApiError(httpStatus))`
     * - I/O 异常 → `Failure(NetworkError)`
     */
    suspend fun fetchProfile(): Result<LearnerProfileDto?> = runCatching {
        api.getProfile()
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it.toAppError()) },
    )

    /**
     * PUT `/api/learner/profile/dimensions/{key}`。
     *
     * 调用方负责保证 [dimensionKey] 是 4 主观维度之一（[ProfileDimensions.SELF_EDITABLE]），
     * 否则后端会返回 403 `PROFILE_DIMENSION_NOT_SELF_EDITABLE`，本方法会原样返回 [Result.Failure]。
     */
    suspend fun updateDimension(
        dimensionKey: String,
        request: DimensionUpdateRequest,
    ): Result<DimensionUpdateResult> = runCatching {
        api.updateDimension(dimensionKey, request)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it.toAppError()) },
    )

    private fun Throwable.toAppError(): AppError = when (this) {
        is HttpException -> ApiError(
            code = "HTTP_${code()}",
            message = message ?: "HTTP error ${code()}",
            httpStatus = code(),
        )
        is IOException -> NetworkError(message = message ?: "Network IO failure")
        else -> UnknownError(message = message ?: this::class.java.simpleName)
    }
}
