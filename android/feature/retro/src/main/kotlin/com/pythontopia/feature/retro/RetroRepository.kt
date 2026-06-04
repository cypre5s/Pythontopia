package com.pythontopia.feature.retro

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RetroApi {
    @GET("api/learner/evaluations")
    suspend fun evaluations(@Query("limit") limit: Int): List<EvaluationWireDto>

    @POST("api/learner/evaluations")
    suspend fun compute(@Body request: EvaluationRequestWireDto): EvaluationWireDto
}

@Module
@InstallIn(SingletonComponent::class)
object RetroNetworkModule {

    @Provides
    @Singleton
    fun provideRetroApi(retrofit: Retrofit): RetroApi = retrofit.create(RetroApi::class.java)
}

/**
 * 复盘数据仓库。
 *
 * Android 端对齐后端 LearnerEvaluation API：读取最近评估，若没有则触发一次 7 天评估。
 */
@Singleton
class RetroRepository @Inject constructor(
    private val api: RetroApi,
) {

    suspend fun thisWeek(
        userId: String,
        weekIndex: Long = currentWeekIndex(),
    ): RetroReport {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(weekIndex >= 0L) { "weekIndex must be >= 0" }
        val latest = api.evaluations(limit = 1).firstOrNull()
            ?: api.compute(EvaluationRequestWireDto(periodDays = DEFAULT_PERIOD_DAYS))
        return latest.toRetroReport()
    }

    suspend fun pastWeeks(
        userId: String,
        count: Int,
        baseWeekIndex: Long = currentWeekIndex(),
    ): List<RetroReport> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(count >= 1) { "count must be >= 1 (requested $count)" }
        require(baseWeekIndex >= 0L) { "baseWeekIndex must be >= 0" }
        return api.evaluations(limit = count + 1)
            .drop(1)
            .take(count)
            .map { it.toRetroReport() }
    }

    suspend fun markRead(reportId: String) {
        require(reportId.isNotBlank()) { "reportId must not be blank" }
    }

    private fun currentWeekIndex(): Long = LocalDate.now().toEpochDay() / DAYS_PER_WEEK

    private companion object {
        const val DEFAULT_PERIOD_DAYS: Int = 7
    }
}

@Serializable
data class EvaluationRequestWireDto(
    val periodDays: Int,
)

@Serializable
data class EvaluationWireDto(
    val id: Long,
    val userId: Long,
    val packId: Long,
    val periodStart: String,
    val periodEnd: String,
    val narrativeSummary: String? = null,
    val pathDiffSummary: String? = null,
    val createdAt: String? = null,
    val actedStatus: String? = null,
    val metrics: List<EvaluationMetricWireDto> = emptyList(),
) {
    fun toRetroReport(): RetroReport {
        val endDate = parseDate(periodEnd) ?: LocalDate.now()
        val attempts = metrics.firstInt("practice_attempts")
            ?: metrics.firstInt("submissions")
            ?: 0
        val bugMineCount = metrics.firstInt("bug_mine_count")
            ?: metrics.firstInt("debug_resolved")
            ?: 0
        return RetroReport(
            reportId = id.toString(),
            userId = userId.toString(),
            weekIndex = endDate.toEpochDay() / DAYS_PER_WEEK,
            periodStart = periodStart.take(10),
            periodEnd = periodEnd.take(10),
            kcGains = metrics.map { metric ->
                KcGain(
                    kcId = metric.metricKey,
                    kcName = metric.notes?.takeIf { it.isNotBlank() } ?: metric.metricKey,
                    masteryDelta = metric.value ?: 0.0,
                    masteryAfter = metric.denominator ?: 0.0,
                )
            }.take(MAX_METRIC_ROWS),
            attempts = attempts,
            bugMineCount = bugMineCount,
            generatedResources = emptyList(),
            isRead = actedStatus != "PENDING",
        )
    }

    private fun List<EvaluationMetricWireDto>.firstInt(key: String): Int? =
        firstOrNull { it.metricKey == key }?.value?.toInt()

    private fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
}

@Serializable
data class EvaluationMetricWireDto(
    val metricKey: String,
    val numerator: Double? = null,
    val denominator: Double? = null,
    val value: Double? = null,
    val displayUnit: String? = null,
    val notes: String? = null,
)

private const val DAYS_PER_WEEK: Long = 7L
private const val MAX_METRIC_ROWS = 10
