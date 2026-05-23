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
 * Workshop（代码工坊）Retrofit 端点契约。
 *
 * 与后端约定（参见 docs/android/01-feature-port-checklist.md §4.8）：
 *   - `GET /api/problems?kcCode=PY43-KC-040` 返回该 KC 关联的题目列表；
 *   - `POST /api/submissions` 提交代码，返回带 PENDING / RUNNING 状态的初始 [SubmissionDto]；
 *   - `GET /api/submissions/{id}` 拉取最新状态，[WorkshopRepository.pollStatus] 据此轮询。
 *
 * Beta 首发严格 server-side judge：Android 端不内置 Pyodide。
 */
interface WorkshopApi {

    @GET("api/problems")
    suspend fun listProblems(@Query("kcCode") kcCode: String): List<ProblemDto>

    @POST("api/submissions")
    suspend fun submit(@Body request: SubmissionCreateRequest): SubmissionDto

    @GET("api/submissions/{id}")
    suspend fun status(@Path("id") submissionId: Long): SubmissionDto

    @GET("api/submissions")
    suspend fun history(@Query("userId") userId: Long): List<SubmissionDto>
}

/**
 * Workshop 业务仓库：把 [WorkshopApi] 包装成 demo 友好的接口，
 * 并对网络异常做静默兜底（沿用 [com.pythontopia.feature.dialog.DialogRepository] 的模式）。
 *
 * 轮询策略：
 *   - [pollStatus] 返回 [Flow]，每隔 [POLL_INTERVAL_MS] 拉一次；
 *   - 终态（[SubmissionStatus.isTerminal]）发射一次后流自动结束；
 *   - 最多轮询 [MAX_POLL_ATTEMPTS] 次后保底结束，防止后端 stuck 时无限循环；
 *   - 拉取失败时静默重试，连续 [MAX_CONSECUTIVE_ERRORS] 次失败后向下游抛出。
 */
@Singleton
class WorkshopRepository @Inject constructor(
    private val api: WorkshopApi,
) {

    suspend fun problems(kcCode: String): List<ProblemDto> =
        runCatching { api.listProblems(kcCode) }.getOrElse { emptyList() }

    suspend fun submit(problemId: Long, code: String): SubmissionDto =
        api.submit(SubmissionCreateRequest(problemId = problemId, code = code))

    /**
     * 轮询直到提交进入终态。
     *
     * 调用方负责把每次 emit 灌进 UI；流自然结束意味着 [SubmissionDto.isTerminal] 为 true 或达到上限。
     */
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
            result.onSuccess { dto ->
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

    suspend fun history(userId: Long): List<SubmissionDto> =
        runCatching { api.history(userId) }.getOrElse { emptyList() }

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
