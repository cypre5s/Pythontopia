package com.pythontopia.feature.profile.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * 学生画像 REST 端点（对照 `ProfileController.java`）。
 *
 * 不在此处的接口：
 *  - 多轮访谈 `/api/learner/profile/interview/...` 见后续 ProfileInterviewApi。
 *  - 历史变更 `/revisions` 见 R-13 班长视角拓展，未来再加。
 *
 * 返回 `LearnerProfileDto?` 而非 `LearnerProfileDto`：后端在未生成画像时返回 `204 No Content`，
 * Retrofit + kotlinx-serialization-converter 会把 204 映射成 null（要求挂起函数返回类型可空）。
 */
interface ProfileApi {

    @GET("api/learner/profile")
    suspend fun getProfile(): Response<LearnerProfileDto>

    /**
     * 8 维画像得分快照。后端恒返回 8 维（[ProfileDimensions.KEYS] 顺序），无 204；
     * 非 2xx 由 Retrofit 抛 [retrofit2.HttpException]，故直接用 `List` 而非 `Response` 包装。
     */
    @GET("api/learner/profile/dimensions")
    suspend fun getDimensions(): List<ProfileDimensionDto>

    @PUT("api/learner/profile/dimensions/{dimensionKey}")
    suspend fun updateDimension(
        @Path("dimensionKey") dimensionKey: String,
        @Body request: DimensionUpdateRequest,
    ): DimensionUpdateResult
}

/**
 * 在 feature 内 provide [ProfileApi]，复用 `core/network/NetworkModule` 已注入的 [Retrofit] 单例
 * （Bearer JWT、序列化 converter、超时配置都在那里统一设置）。
 *
 * 之所以放 feature 而非 core/network：profile 端点目前只有 profile feature 直接消费，
 * 避免 core/network 因业务接口扩散而臃肿；如未来 classroom / path 也需要，再上提到 core。
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileNetworkModule {

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)
}
