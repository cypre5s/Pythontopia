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

/** 包装 [ProfileApi] 并将异常归一为 [Result]；失败须上报，不做静默 fallback（对照 DialogRepository）。 */
@Singleton
class ProfileRepository @Inject constructor(
    private val api: ProfileApi,
) {

    /** GET `/api/learner/profile`；204 映射 Success(null)（未完成访谈）。 */
    suspend fun fetchProfile(): Result<LearnerProfileDto?> = runCatching {
        val response = api.getProfile()
        if (response.code() == HTTP_NO_CONTENT) {
            return@runCatching null
        }
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        response.body()
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it.toAppError()) },
    )

    /** GET `/api/learner/profile/dimensions`；恒返回 8 维快照，不会 204。 */
    suspend fun fetchDimensions(): Result<List<ProfileDimensionDto>> = runCatching {
        api.getDimensions()
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

    private companion object {
        const val HTTP_NO_CONTENT = 204
    }
}
