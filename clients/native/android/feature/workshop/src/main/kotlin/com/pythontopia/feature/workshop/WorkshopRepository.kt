package com.pythontopia.feature.workshop

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit 端点契约：
 * `GET /api/course/problems?kcCode=` 题目列表；
 * `POST /api/submissions` 提交代码（初始 PENDING/RUNNING）；
 * `GET /api/submissions/{id}` 判题详情，供 [pollStatus] 轮询。
 * Beta 仅服务端判题，不内置 Pyodide。
 */
interface WorkshopApi {

    @GET("api/course/problems")
    suspend fun listProblems(@Query("kcCode") kcCode: String): List<ProblemDto>

    @POST("api/submissions")
    suspend fun submit(@Body request: SubmissionCreateRequest): SubmissionDto

    @GET("api/submissions/{id}")
    suspend fun status(@Path("id") submissionId: Long): SubmissionDetailDto

    @GET("api/submissions")
    suspend fun history(): List<SubmissionDto>
}

/**
 * 业务仓库：列表/历史请求失败静默返回空表；[pollStatus] 按 [POLL_INTERVAL_MS] 轮询至终态或 [MAX_POLL_ATTEMPTS] 上限，
 * 连续 [MAX_CONSECUTIVE_ERRORS] 次拉取失败后向上游抛出。
 */
@Singleton
class WorkshopRepository @Inject constructor(
    private val api: WorkshopApi,
) {

    suspend fun problems(kcCode: String): List<ProblemDto> =
        runCatching { api.listProblems(kcCode) }.getOrElse { emptyList() }

    suspend fun submit(problemId: Long, code: String): SubmissionDto =
        api.submit(SubmissionCreateRequest(problemId = problemId, code = code))

    /** 轮询至 [SubmissionDto.isTerminal] 或达到 [maxAttempts]；每次 emit 由调用方写入 UI。 */
    fun pollStatus(
        submissionId: Long,
        intervalMs: Long = POLL_INTERVAL_MS,
        maxAttempts: Int = MAX_POLL_ATTEMPTS,
    ): Flow<SubmissionDto> = flow {
        var attempts = 0
        var consecutiveErrors = 0
        while (attempts < maxAttempts) {
            attempts++
            val result = runCatching { api.status(submissionId) }
            result.onSuccess { detail ->
                val dto = detail.toSubmissionDto()
                consecutiveErrors = 0
                emit(dto)
                if (dto.isTerminal) {
                    return@flow
                }
            }
            result.onFailure {
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    throw it
                }
            }
            delay(intervalMs)
        }
    }

    suspend fun history(): List<SubmissionDto> =
        runCatching { api.history() }.getOrElse { emptyList() }

    companion object {
        const val POLL_INTERVAL_MS: Long = 1_200L
        const val MAX_POLL_ATTEMPTS: Int = 30
        const val MAX_CONSECUTIVE_ERRORS: Int = 3
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WorkshopNetworkModule {

    @Provides
    @Singleton
    fun provideWorkshopApi(retrofit: Retrofit): WorkshopApi =
        retrofit.create(WorkshopApi::class.java)
}
