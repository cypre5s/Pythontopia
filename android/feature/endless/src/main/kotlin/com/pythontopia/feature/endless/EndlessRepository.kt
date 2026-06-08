package com.pythontopia.feature.endless

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

interface EndlessApi {

    @GET("api/course/kcs")
    suspend fun kcs(): List<EndlessKcDto>

    @GET("api/course/problems")
    suspend fun problems(@Query("kcCode") kcCode: String): List<EndlessProblemDto>

    @POST("api/submissions")
    suspend fun submit(@Body body: EndlessSubmissionCreate): EndlessSubmissionDto

    @GET("api/submissions/{id}")
    suspend fun status(@Path("id") id: Long): EndlessSubmissionDetail
}

/** 无尽练习仓库：抽 KC/题目、提交判题、轮询终态；读路径失败静默兜底。 */
@Singleton
class EndlessRepository @Inject constructor(
    private val api: EndlessApi,
) {
    suspend fun kcCodes(): List<String> =
        runCatching { api.kcs().map { it.kcCode } }.getOrElse { emptyList() }

    suspend fun problems(kcCode: String): List<EndlessProblemDto> =
        runCatching { api.problems(kcCode) }.getOrElse { emptyList() }

    suspend fun submit(problemId: Long, code: String): EndlessSubmissionDto =
        api.submit(EndlessSubmissionCreate(problemId = problemId, code = code))

    fun pollStatus(
        submissionId: Long,
        intervalMs: Long = POLL_INTERVAL_MS,
        maxAttempts: Int = MAX_POLL_ATTEMPTS,
    ): Flow<EndlessSubmissionDto> = flow {
        var attempts = 0
        var consecutiveErrors = 0
        while (attempts < maxAttempts) {
            attempts++
            val result = runCatching { api.status(submissionId) }
            result.onSuccess { detail ->
                consecutiveErrors = 0
                val dto = detail.merged()
                emit(dto)
                if (dto.isTerminal) return@flow
            }
            result.onFailure {
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) throw it
            }
            delay(intervalMs)
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 1_200L
        const val MAX_POLL_ATTEMPTS = 30
        const val MAX_CONSECUTIVE_ERRORS = 3
    }
}

@Module
@InstallIn(SingletonComponent::class)
object EndlessNetworkModule {

    @Provides
    @Singleton
    fun provideEndlessApi(retrofit: Retrofit): EndlessApi =
        retrofit.create(EndlessApi::class.java)
}
