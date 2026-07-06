package com.pythontopia.feature.problems

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

private const val SUBMISSION_LIMIT = 200

internal interface ProblemsApi {
    @GET("api/course/problems")
    suspend fun problems(@Query("kcCode") kcCode: String? = null): List<ProblemWireDto>

    @GET("api/course/kcs")
    suspend fun kcs(): List<KcWireDto>

    @GET("api/submissions")
    suspend fun submissions(@Query("limit") limit: Int = SUBMISSION_LIMIT): List<SubmissionWireDto>
}

/**
 * 题库仓库：合并题目 + KC（章节/名称）+ 提交记录（玩家进度）。
 * 题目与 KC 为必需（失败上抛 [Result]）；提交记录尽力而为（鉴权/网络失败回退空进度，不阻塞浏览）。
 */
@Singleton
class ProblemsRepository @Inject internal constructor(
    private val api: ProblemsApi,
) {
    suspend fun load(): Result<List<ProblemRow>> = runCatching {
        val problems = api.problems(null)
        val kcByCode = api.kcs().associateBy { it.kcCode }
        val progress = loadProgress()
        problems.map { p ->
            val kc = kcByCode[p.kcCode]
            val prog = progress[p.problemId]
            ProblemRow(
                problemId = p.problemId,
                title = p.title,
                kcCode = p.kcCode,
                kcName = kc?.kcName?.takeIf { it.isNotBlank() } ?: p.kcCode,
                chapterOrder = kc?.chapterOrder,
                difficulty = ProblemDifficulty.fromBackend(p.difficulty),
                statementMarkdown = p.statementMarkdown,
                inputDescription = p.inputDescription,
                outputDescription = p.outputDescription,
                sourceLabel = p.sourceLabel,
                status = prog?.status ?: ProblemStatus.NOT_TRIED,
                attempts = prog?.attempts ?: 0,
            )
        }
    }

    private data class Progress(val status: ProblemStatus, val attempts: Int)

    private suspend fun loadProgress(): Map<Long, Progress> {
        val submissions = runCatching { api.submissions() }.getOrElse { return emptyMap() }
        val acc = HashMap<Long, Progress>()
        for (s in submissions) {
            val prev = acc[s.problemId]
            val attempts = (prev?.attempts ?: 0) + 1
            val status = mergeStatus(prev?.status ?: ProblemStatus.NOT_TRIED, s.status)
            acc[s.problemId] = Progress(status, attempts)
        }
        return acc
    }

    private fun mergeStatus(prev: ProblemStatus, incoming: String?): ProblemStatus = when {
        prev == ProblemStatus.PASSED -> ProblemStatus.PASSED
        incoming.equals("PASSED", ignoreCase = true) -> ProblemStatus.PASSED
        incoming.equals("FAILED", ignoreCase = true) || incoming.equals("ERROR", ignoreCase = true) ->
            ProblemStatus.FAILED
        else -> prev
    }
}

@Serializable
internal data class ProblemWireDto(
    val problemId: Long,
    val kcCode: String = "",
    val title: String = "",
    val statementMarkdown: String = "",
    val inputDescription: String = "",
    val outputDescription: String = "",
    val difficulty: String = "",
    val sourceLabel: String = "",
)

@Serializable
internal data class KcWireDto(
    val kcId: Long = 0L,
    val kcCode: String = "",
    val kcName: String = "",
    val chapterId: Long = 0L,
    val chapterOrder: Int? = null,
    val difficulty: String? = null,
    val summaryMarkdown: String = "",
)

@Serializable
internal data class SubmissionWireDto(
    val id: Long = 0L,
    val problemId: Long = 0L,
    val status: String = "",
)

@Module
@InstallIn(SingletonComponent::class)
internal object ProblemsNetworkModule {

    @Provides
    @Singleton
    fun provideProblemsApi(retrofit: Retrofit): ProblemsApi =
        retrofit.create(ProblemsApi::class.java)
}
