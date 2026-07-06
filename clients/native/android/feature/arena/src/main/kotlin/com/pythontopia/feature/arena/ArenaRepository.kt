package com.pythontopia.feature.arena

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface ArenaApi {
    @GET("api/course/problems")
    suspend fun problems(@Query("kcCode") kcCode: String): List<ArenaProblemWireDto>

    @POST("api/submissions")
    suspend fun submit(@Body request: ArenaSubmissionCreateRequest): ArenaSubmissionWireDto

    // POST 后轮询直至 submission.status 进入终态
    @GET("api/submissions/{id}")
    suspend fun submissionDetail(@Path("id") id: Long): ArenaSubmissionDetailWireDto

    // 仅支持 problemId/status/limit/offset，无 classroomId 过滤与跨用户排行榜
    @GET("api/submissions")
    suspend fun submissions(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = HISTORY_LIMIT,
    ): List<ArenaSubmissionWireDto>
}

/** 训练场仓库：题目来自 pack 43，提交走 `/api/submissions` 与服务端判题同源。 */
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

    /**
     * 提交并轮询终态：status ∈ {PASSED, FAILED, ERROR}。
     * detail 含 judgeResult 判题明细，POST 响应本身不含。
     */
    suspend fun submit(problemId: Long, code: String): Submission {
        val created = api.submit(
            ArenaSubmissionCreateRequest(
                problemId = problemId,
                code = code,
                source = SUBMISSION_SOURCE,
            ),
        )
        var attempts = 0
        while (true) {
            val detail = api.submissionDetail(created.id)
            val status = detail.submission.status
            val terminal =
                status == STATUS_PASSED || status == STATUS_FAILED || status == STATUS_RUNTIME_ERROR
            if (terminal || attempts >= MAX_POLL_ATTEMPTS) {
                return detail.toSubmission()
            }
            attempts++
            delay(POLL_INTERVAL_MS)
        }
    }

    /** local-only 个人战绩：由 PASSED 提交去重已解题数，[SCORE_PER_SOLVED] 换算展示积分。 */
    suspend fun leaderboard(): List<LeaderboardEntry> {
        val accepted = api.submissions(status = STATUS_PASSED)
        val solved = accepted.map { it.problemId }.distinct().size
        return listOf(
            LeaderboardEntry(
                rank = 1,
                userId = accepted.firstOrNull()?.userId ?: CURRENT_USER_PLACEHOLDER_ID,
                displayName = "当前账号",
                score = solved * SCORE_PER_SOLVED,
                solvedCount = solved,
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
        private const val POLL_INTERVAL_MS = 1_200L
        private const val MAX_POLL_ATTEMPTS = 30

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

// 对齐后端 SubmissionDto（camelCase）；其余字段由 ignoreUnknownKeys 丢弃
@Serializable
internal data class ArenaSubmissionWireDto(
    val id: Long,
    val userId: Long? = null,
    val problemId: Long,
    val code: String? = null,
    val status: String,
    val runtimeMs: Int? = null,
)

// 对齐 SubmissionDetailDto：{ submission, judgeResult? }
@Serializable
internal data class ArenaSubmissionDetailWireDto(
    val submission: ArenaSubmissionWireDto,
    val judgeResult: ArenaJudgeResultWireDto? = null,
) {
    fun toSubmission(): Submission {
        val s = submission
        val cases = judgeResult
            ?.takeIf { it.passedCases != null && it.totalCases != null }
            ?.let { "（${it.passedCases}/${it.totalCases} 用例）" }
            .orEmpty()
        val message = when (s.status) {
            ArenaRepository.STATUS_PASSED ->
                "服务端判题通过$cases" + (s.runtimeMs?.let { "，耗时 ${it}ms" } ?: "")
            ArenaRepository.STATUS_FAILED ->
                judgeResult?.failureReason?.takeIf { it.isNotBlank() }
                    ?: "服务端判题未通过$cases，请检查题目样例和边界条件"
            ArenaRepository.STATUS_RUNTIME_ERROR ->
                judgeResult?.failureReason?.takeIf { it.isNotBlank() }
                    ?: "服务端运行出错，请检查异常和输入处理"
            else -> "提交已进入服务端状态：${s.status}"
        }
        return Submission(
            submissionId = s.id,
            problemId = s.problemId,
            status = s.status,
            message = message,
            scoreAwarded = if (s.status == ArenaRepository.STATUS_PASSED) SCORE_PER_ARENA_SUBMISSION else 0,
        )
    }
}

// 对齐 JudgeResultDto；failureReason 用作可读失败信息
@Serializable
internal data class ArenaJudgeResultWireDto(
    val verdict: String? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
    val failureReason: String? = null,
)

private const val HISTORY_LIMIT = 100
private const val SCORE_PER_ARENA_SUBMISSION = 100
