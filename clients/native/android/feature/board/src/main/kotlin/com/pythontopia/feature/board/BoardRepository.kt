package com.pythontopia.feature.board

import com.pythontopia.core.data.dto.learner.QuestDto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface BoardApi {
    @GET("api/quests/today")
    suspend fun today(): List<QuestDto>

    @POST("api/quests/{id}/start")
    suspend fun start(@Path("id") id: Long): QuestDto

    @POST("api/quests/{id}/complete")
    suspend fun complete(@Path("id") id: Long, @Body body: QuestCompleteBody): QuestDto

    @POST("api/quests/{id}/skip")
    suspend fun skip(@Path("id") id: Long, @Body body: QuestSkipBody): QuestDto
}

/**
 * 公告板仓库：今日任务 + 开始/完成/跳过。完成强校验由后端 [QuestService] 执行——
 * 需 evidence 的任务（requiredEvidenceType 非空）端侧不提供「无证据完成」，避免伪造完成（AGENTS.md）。
 */
@Singleton
class BoardRepository @Inject internal constructor(
    private val api: BoardApi,
) {
    suspend fun today(): Result<List<QuestDto>> = runCatching { api.today() }

    suspend fun start(id: Long): Result<QuestDto> = runCatching { api.start(id) }

    suspend fun complete(id: Long, note: String? = null): Result<QuestDto> =
        runCatching { api.complete(id, QuestCompleteBody(note = note)) }

    suspend fun skip(id: Long, reason: String? = null): Result<QuestDto> =
        runCatching { api.skip(id, QuestSkipBody(reason = reason)) }
}

@Serializable
internal data class QuestCompleteBody(val note: String? = null)

@Serializable
internal data class QuestSkipBody(val reason: String? = null)

@Module
@InstallIn(SingletonComponent::class)
internal object BoardNetworkModule {

    @Provides
    @Singleton
    fun provideBoardApi(retrofit: Retrofit): BoardApi = retrofit.create(BoardApi::class.java)
}
