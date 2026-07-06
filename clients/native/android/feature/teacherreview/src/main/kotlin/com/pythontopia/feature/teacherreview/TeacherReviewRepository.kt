package com.pythontopia.feature.teacherreview

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

internal interface TeacherReviewApi {
    @POST("api/world/boss/{chapter}/problem")
    suspend fun problem(
        @Path("chapter") chapter: Int,
        @Body body: BossProblemRequestBody,
    ): ReviewProblemWireDto
}

/**
 * 教师审题仓库：通过 boss 出题端点拉一道 AI/golden 题供审核。
 * 出题失败上抛 [Result]（fail fast）；审核决定不入库，由 ViewModel 会话本地维护。
 */
@Singleton
class TeacherReviewRepository @Inject internal constructor(
    private val api: TeacherReviewApi,
) {
    suspend fun fetchProblem(chapter: Int, stage: Int): Result<ReviewProblem> = runCatching {
        api.problem(chapter, BossProblemRequestBody(stage = stage)).toReviewProblem()
    }
}

@Serializable
internal data class BossProblemRequestBody(val stage: Int)

@Serializable
internal data class ReviewProblemWireDto(
    val problemId: String = "",
    val chapter: Int = 0,
    val stage: Int = 0,
    val difficulty: Int = 0,
    val kcCode: String? = null,
    val statement: String = "",
    val hints: List<String> = emptyList(),
    val sampleInput: String? = null,
    val sampleOutput: String? = null,
    val totalTestCases: Int = 0,
    val reviewed: Boolean = false,
    val aiGenerated: Boolean = false,
) {
    fun toReviewProblem(): ReviewProblem = ReviewProblem(
        problemId = problemId,
        chapter = chapter,
        stage = stage,
        difficulty = difficulty,
        kcCode = kcCode,
        statement = statement,
        hints = hints,
        sampleInput = sampleInput,
        sampleOutput = sampleOutput,
        totalTestCases = totalTestCases,
        reviewed = reviewed,
        aiGenerated = aiGenerated,
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal object TeacherReviewNetworkModule {

    @Provides
    @Singleton
    fun provideTeacherReviewApi(retrofit: Retrofit): TeacherReviewApi =
        retrofit.create(TeacherReviewApi::class.java)
}
