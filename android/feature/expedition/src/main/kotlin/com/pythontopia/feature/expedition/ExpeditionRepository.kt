package com.pythontopia.feature.expedition

import android.content.Context
import com.pythontopia.core.data.dto.learner.MasterySnapshotDto
import com.pythontopia.core.data.dto.mainline.EndlessSummaryRequest
import com.pythontopia.core.data.dto.mainline.StarAwardResultDto
import com.pythontopia.core.network.api.MainlineApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ExpeditionApi {

    @POST("api/world/boss/select-frontier")
    suspend fun selectFrontier(@Body body: FrontierSelectRequest): FrontierSelectResultDto

    @GET("api/world/expedition/run")
    suspend fun getRun(): Response<ExpeditionRunSnapshot>

    @PUT("api/world/expedition/run")
    suspend fun putRun(@Body run: ExpeditionRunSnapshot): ExpeditionRunSnapshot

    @DELETE("api/world/expedition/run")
    suspend fun deleteRun(): Response<Unit>

    @POST("api/world/boss/{chapter}/problem")
    suspend fun problem(@Path("chapter") chapter: Int, @Body body: ExpeditionProblemRequest): ExpeditionProblemDto

    @POST("api/world/boss/{chapter}/attempt")
    suspend fun attempt(@Path("chapter") chapter: Int, @Body body: ExpeditionAttemptRequest): ExpeditionAttemptResultDto

    @GET("api/learner/mastery")
    suspend fun mastery(): List<MasterySnapshotDto>
}

/**
 * 无尽远征仓库：知识前沿候选、run 跨会话持久化（后端权威 + 本地兜底）、出题/判题（复用 boss 闭环）、
 * 掌握度（本地前沿兜底 + 知识星座）、补星结算。所有「best-effort」路径失败静默回退，绝不阻塞玩家。
 */
@Singleton
class ExpeditionRepository @Inject constructor(
    private val api: ExpeditionApi,
    private val mainlineApi: MainlineApi,
    private val local: ExpeditionLocalStore,
) {
    /** 取一批知识前沿候选；失败返回空（调用方回退本地 [KcFrontier]）。 */
    suspend fun requestFrontierPicks(
        focusChapter: Int?,
        recentKcs: List<String>,
        count: Int,
    ): List<FrontierPick> = runCatching {
        api.selectFrontier(
            FrontierSelectRequest(focusChapter = focusChapter, recentKcs = recentKcs, count = count),
        ).picks
    }.getOrElse { emptyList() }

    /** 取当前用户活跃 run：后端优先，不可达回退本地缓存。无活跃 run（204/空）返回 null。 */
    suspend fun fetchRun(): ExpeditionRunSnapshot? {
        val remote = runCatching {
            val res = api.getRun()
            if (res.code() == NO_CONTENT || !res.isSuccessful) null else res.body()
        }.getOrElse { return local.load() }
        return remote ?: local.load()
    }

    /** 保存 run：本地立即落盘 + 后端 best-effort upsert（非 idle 才镜像后端）。 */
    suspend fun saveRun(run: ExpeditionRunSnapshot) {
        local.save(run)
        if (run.phase != RogueRunPhase.IDLE) {
            runCatching { api.putRun(run) }
        }
    }

    /** 弃局：清本地缓存 + 后端 best-effort DELETE。 */
    suspend fun deleteRun() {
        local.clear()
        runCatching { api.deleteRun() }
    }

    /** 按 (KC, 题型, 难度) 出题：章节由 KC 推断，stage = clamp(round(difficulty),1,4)。 */
    suspend fun fetchProblem(kc: String, kind: String?, difficulty: Int): Result<ExpeditionProblemDto> {
        val chapter = RogueConfig.kcChapter(kc)
        val stage = difficulty.coerceIn(1, 4)
        return runCatching {
            api.problem(
                chapter,
                ExpeditionProblemRequest(
                    stage = stage,
                    preferredKc = kc,
                    preferredKind = kind,
                    difficulty = difficulty,
                ),
            )
        }
    }

    suspend fun judge(problem: ExpeditionProblemDto, code: String, elapsedSec: Int): Result<ExpeditionAttemptResultDto> =
        runCatching {
            api.attempt(
                problem.chapter,
                ExpeditionAttemptRequest(
                    problemId = problem.problemId,
                    chapter = problem.chapter,
                    stage = problem.stage,
                    code = code,
                    elapsedSec = elapsedSec,
                ),
            )
        }

    /** 掌握度快照（本地前沿兜底 + 知识星座）；失败返回空。 */
    suspend fun fetchMastery(): Map<String, Double> = runCatching {
        api.mastery().mapNotNull { dto -> dto.masteryScore?.let { dto.kcCode to it } }.toMap()
    }.getOrElse { emptyMap() }

    /** 远征小结 → 补星判定（聚焦章达阈值时触发）；失败返回 null。 */
    suspend fun submitEndlessSummary(chapter: Int, correct: Int, total: Int, streak: Int): StarAwardResultDto? =
        runCatching { mainlineApi.endlessSummary(EndlessSummaryRequest(chapter, correct, total, streak)) }.getOrNull()

    /** 每日挑战是否仍可用（当日未完成）。本地记录日期（镜像 Web localStorage）。 */
    fun isDailyAvailable(): Boolean = local.dailyDoneDate() != todayStr()

    fun markDailyDone() {
        local.setDailyDoneDate(todayStr())
    }

    fun todayStr(): String = LocalDate.now().toString()

    private companion object {
        const val NO_CONTENT = 204
    }
}

/** 判题不可达（后端兜底）判定：不应据此扣血/计分，交由调用方提示重试。对齐 Web isJudgeUnavailable。 */
fun ExpeditionAttemptResultDto.isJudgeUnavailable(): Boolean =
    !passed && score == 0 && feedback.contains("后端未连接")

/** run 进度本地缓存（SharedPreferences；镜像 Web localStorage 兜底）。 */
@Singleton
class ExpeditionLocalStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun load(): ExpeditionRunSnapshot? {
        val raw = prefs.getString(KEY_RUN, null) ?: return null
        return runCatching { json.decodeFromString(ExpeditionRunSnapshot.serializer(), raw) }.getOrNull()
    }

    fun save(run: ExpeditionRunSnapshot) {
        runCatching {
            prefs.edit().putString(KEY_RUN, json.encodeToString(ExpeditionRunSnapshot.serializer(), run)).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_RUN).apply()
    }

    fun dailyDoneDate(): String? = prefs.getString(KEY_DAILY, null)

    fun setDailyDoneDate(date: String) {
        prefs.edit().putString(KEY_DAILY, date).apply()
    }

    private companion object {
        const val PREFS = "pyt_expedition"
        const val KEY_RUN = "run_v1"
        const val KEY_DAILY = "daily_v1"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ExpeditionNetworkModule {

    @Provides
    @Singleton
    fun provideExpeditionApi(retrofit: Retrofit): ExpeditionApi =
        retrofit.create(ExpeditionApi::class.java)
}
