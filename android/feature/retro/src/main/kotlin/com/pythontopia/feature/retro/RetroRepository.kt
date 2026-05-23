package com.pythontopia.feature.retro

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * `GET /api/learner/retro/weekly?weekIndex=X&userId=Y` + 已读回执的 Retrofit 契约。
 *
 * 与 `:core:network:NpcDialogApi` 对称：interface 留在 feature 模块内，
 * 是因为只有 retro 用到这两个端点，避免污染 `:core:network` 的 API 表面。
 * Retrofit 实例仍复用 `NetworkModule.provideRetrofit` 提供的共享 OkHttp + Json 配置，
 * 所以 JWT、超时、日志拦截器一致。
 */
interface RetroApi {

    @GET("api/learner/retro/weekly")
    suspend fun weeklyReport(
        @Query("weekIndex") weekIndex: Long,
        @Query("userId") userId: String,
    ): RetroReport

    @POST("api/learner/retro/{reportId}/read")
    suspend fun markRead(@Path("reportId") reportId: String)
}

/**
 * 在 retro feature 内挂载 [RetroApi]，注入共享 [Retrofit] 单例。
 *
 * 选择 feature-scoped Hilt 模块而不是把 `provideRetroApi` 推回 `:core:network`，
 * 是 ADR-001 §3「feature 自治」决策的延伸：retro 是 P2 范围，可独立删/换。
 */
@Module
@InstallIn(SingletonComponent::class)
object RetroNetworkModule {

    @Provides
    @Singleton
    fun provideRetroApi(retrofit: Retrofit): RetroApi = retrofit.create(RetroApi::class.java)
}

/**
 * 周复盘数据仓库。
 *
 *  - [thisWeek] 按客户端当前 ISO 周拉取本周报告，`weekIndex` 默认值便于测试注入；
 *  - [pastWeeks] 顺序请求最近 `count` 周（不并发：周报告生成 8-20s，并发反而触发后端节流）；
 *  - [markRead] 标记已读，幂等；网络错误抛给上层，由 ViewModel 决定 UI 提示。
 *
 * 不提供降级 / 假数据：教育闭环要求复盘必须基于真实学习事件，
 * 一旦 fallback 假数据会污染遗忘曲线统计（违反 AGENTS.md `## 禁止事项 §4`）。
 */
@Singleton
class RetroRepository @Inject constructor(
    private val api: RetroApi,
) {

    suspend fun thisWeek(
        userId: String,
        weekIndex: Long = currentWeekIndex(),
    ): RetroReport = api.weeklyReport(weekIndex = weekIndex, userId = userId)

    suspend fun pastWeeks(
        userId: String,
        count: Int,
        baseWeekIndex: Long = currentWeekIndex(),
    ): List<RetroReport> {
        require(count >= 1) { "count must be >= 1 (requested $count)" }
        return (1..count).map { offset ->
            api.weeklyReport(weekIndex = baseWeekIndex - offset, userId = userId)
        }
    }

    suspend fun markRead(reportId: String) {
        require(reportId.isNotBlank()) { "reportId must not be blank" }
        api.markRead(reportId)
    }

    /**
     * Long = `epochDay / 7`，与服务端 `LearnerEvaluationService.weekIndexOf(LocalDate)` 对齐，
     * 不依赖 ISO 周年份切换，跨年仍单调递增，便于直接做 `base - offset` 算往周。
     */
    private fun currentWeekIndex(): Long = LocalDate.now().toEpochDay() / DAYS_PER_WEEK

    private companion object {
        const val DAYS_PER_WEEK: Long = 7L
    }
}
