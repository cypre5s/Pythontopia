package com.pythontopia.feature.feynman

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface FeynmanApi {

    @POST("api/feynman/sessions")
    suspend fun start(@Body body: StartFeynmanSessionRequest): FeynmanSessionDto

    @POST("api/feynman/sessions/{id}/teach")
    suspend fun teach(
        @Path("id") id: Long,
        @Body body: TeachFeynmanSessionRequest,
    ): FeynmanTurnDto

    @POST("api/feynman/sessions/{id}/judge")
    suspend fun judge(@Path("id") id: Long): FeynmanVerdictDto

    @POST("api/feynman/sessions/{id}/bug-hunt")
    suspend fun bugHunt(@Path("id") id: Long): FeynmanBugHuntDto
}

/**
 * 费曼反演仓库：开会话 / 教学 / 真判题。出题与判题均依赖后端（graph + 服务端判题），
 * 失败一律上抛 [Result] 而非静默兜空，符合 AGENTS.md「fail fast / 不伪造」约束。
 */
@Singleton
class FeynmanRepository @Inject constructor(
    private val api: FeynmanApi,
) {
    suspend fun startSession(kcCode: String): Result<FeynmanSessionDto> =
        runCatching { api.start(StartFeynmanSessionRequest(kcCode = kcCode)) }

    suspend fun teach(sessionId: Long, explanation: String): Result<FeynmanTurnDto> =
        runCatching { api.teach(sessionId, TeachFeynmanSessionRequest(explanation = explanation)) }

    suspend fun judge(sessionId: Long): Result<FeynmanVerdictDto> =
        runCatching { api.judge(sessionId) }

    /** 代码找茬样本（后端按真实误解/常见陷阱生成含 bug 代码）。 */
    suspend fun bugHunt(sessionId: Long): Result<FeynmanBugHuntDto> =
        runCatching { api.bugHunt(sessionId) }
}

@Module
@InstallIn(SingletonComponent::class)
object FeynmanNetworkModule {

    @Provides
    @Singleton
    fun provideFeynmanApi(retrofit: Retrofit): FeynmanApi =
        retrofit.create(FeynmanApi::class.java)
}
