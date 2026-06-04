package com.pythontopia.feature.arena

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

internal interface ArenaApi {
    @GET("api/course/problems")
    suspend fun problems(@Query("kcCode") kcCode: String): List<ArenaProblemWireDto>

    @POST("api/submissions")
    suspend fun submit(@Body request: ArenaSubmissionCreateRequest): ArenaSubmissionWireDto

    @GET("api/submissions")
    suspend fun submissions(
        @Query("status") status: String? = null,
        @Query("classroomId") classroomId: Long? = null,
        @Query("limit") limit: Int = HISTORY_LIMIT,
    ): List<ArenaSubmissionWireDto>
}

/**
 * 训练场仓库。
 *
 * Android 端不再本地模拟判题：题目来自 pack 43 课程题库，提交统一走后端
 * `/api/submissions`，确保正式练习、画像事件、班级统计与 Web/后端同源。
 */
@Singleton
class ArenaRepository @Inject internal constructor(
    private val api: ArenaApi,
) {

    suspend fun todaysSession(): ArenaSession {
        val problems = DEFAULT_KC_CODES.flatMap { kcCode ->
            api.problems(kcCode).take(PROBLEMS_PER_KC).map { it.toArenaProblem() }
        }.distinctBy { it.problemId }
            .take(PROBLEM_LIMIT)
        val now = System.currentTimeMillis()
        return ArenaSession(
            sessionId = TODAY_SESSION_ID,
            title = "Python 课程训练场",
            classroomId = null,
            startEpochMs = now - SESSION_GRACE_MS,
            endEpochMs = now + SESSION_REMAINING_MS,
            problems = problems,
        )
    }

    suspend fun submit(problemId: Long, code: String): Submission {
        val response = api.submit(
            ArenaSubmissionCreateRequest(
                problemId = problemId,
                code = code,
                source = SUBMISSION_SOURCE,
            ),
        )
        return response.toSubmission()
    }

    suspend fun leaderboard(classroomId: Long?): List<LeaderboardEntry> {
        val accepted = api.submissions(status = STATUS_PASSED, classroomId = classroomId)
        val score = accepted.map { it.problemId }.distinct().size * SCORE_PER_SOLVED
        return listOf(
            LeaderboardEntry(
                rank = 1,
                userId = accepted.firstOrNull()?.userId ?: CURRENT_USER_PLACEHOLDER_ID,
                displayName = "当前账号",
                score = score,
                solvedCount = accepted.map { it.problemId }.distinct().size,
            ),
        )
    }

    companion object {
        const val STATUS_PASSED = "PASSED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_RUNTIME_ERROR = "ERROR"

        private const val SUBMISSION_SOURCE = "TRAINING"
        private const val TODAY_SESSION_ID = 43L
        private const val SESSION_GRACE_MS = 60_000L
        private const val SESSION_REMAINING_MS = 30L * 60L * 1000L
        private const val PROBLEMS_PER_KC = 1
        private const val PROBLEM_LIMIT = 3
        private const val SCORE_PER_SOLVED = 100
        private const val CURRENT_USER_PLACEHOLDER_ID = -1L

        private val DEFAULT_KC_CODES = listOf(
            "PY43-KC-016",
            "PY43-KC-022",
            "PY43-KC-033",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object ArenaNetworkModule {
    @Provides
    @Singleton
    fun provideArenaApi(retrofit: Retrofit): ArenaApi = retrofit.create(ArenaApi::class.java)
}

@Serializable
internal data class ArenaProblemWireDto(
    @SerialName("problemId")
    val problemId: Long,
    val title: String,
    val statementMarkdown: String,
    val difficulty: String? = null,
) {
    fun toArenaProblem(): ArenaProblem =
        ArenaProblem(
            problemId = problemId,
            title = title,
            statementMd = statementMarkdown,
            starterCode = "# 在这里写下你的 Python 解法\n",
            scoreReward = when (difficulty?.lowercase()) {
                "high", "hard" -> 200
                "medium", "mid" -> 150
                else -> 100
            },
        )
}

@Serializable
internal data class ArenaSubmissionCreateRequest(
    val problemId: Long,
    val code: String,
    val source: String,
)

@Serializable
internal data class ArenaSubmissionWireDto(
    val id: Long,
    val userId: Long? = null,
    val problemId: Long,
    val code: String? = null,
    val status: String,
    val runtimeMs: Int? = null,
) {
    fun toSubmission(): Submission =
        Submission(
            submissionId = id,
            problemId = problemId,
            status = status,
            message = when (status) {
                ArenaRepository.STATUS_PASSED -> runtimeMs?.let { "服务端判题通过，耗时 ${it}ms" }
                    ?: "服务端判题通过"
                ArenaRepository.STATUS_FAILED -> "服务端判题未通过，请查看题目样例和边界条件"
                ArenaRepository.STATUS_RUNTIME_ERROR -> "服务端运行出错，请检查异常和输入处理"
                else -> "提交已进入服务端状态：$status"
            },
            scoreAwarded = if (status == ArenaRepository.STATUS_PASSED) SCORE_PER_ARENA_SUBMISSION else 0,
        )
}

private const val HISTORY_LIMIT = 100
private const val SCORE_PER_ARENA_SUBMISSION = 100
