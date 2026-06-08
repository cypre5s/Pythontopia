package com.pythontopia.feature.boss

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

interface BossApi {

    @POST("api/world/boss/{chapter}/problem")
    suspend fun problem(
        @Path("chapter") chapter: Int,
        @Body body: BossProblemRequestBody,
    ): BossProblemDto

    @POST("api/world/boss/{chapter}/attempt")
    suspend fun attempt(
        @Path("chapter") chapter: Int,
        @Body body: BossAttemptRequestBody,
    ): BossAttemptResultDto
}

/**
 * BOSS 战仓库：出题依赖 graph PracticeAgent，失败须上抛 [Result] 而非静默兜空；判题走服务端。
 */
@Singleton
class BossRepository @Inject constructor(
    private val api: BossApi,
) {
    suspend fun fetchProblem(chapter: Int, stage: Int): Result<BossProblemDto> =
        runCatching { api.problem(chapter, BossProblemRequestBody(stage = stage)) }

    suspend fun submitAttempt(
        chapter: Int,
        stage: Int,
        problemId: String,
        code: String,
        elapsedSec: Int,
    ): Result<BossAttemptResultDto> = runCatching {
        api.attempt(
            chapter,
            BossAttemptRequestBody(
                problemId = problemId,
                chapter = chapter,
                stage = stage,
                code = code,
                elapsedSec = elapsedSec,
            ),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BossNetworkModule {

    @Provides
    @Singleton
    fun provideBossApi(retrofit: Retrofit): BossApi =
        retrofit.create(BossApi::class.java)
}
