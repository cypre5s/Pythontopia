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
 * GET /profile 未生成画像时返回 204，Retrofit 映射为 null，故挂起函数返回 `Response<LearnerProfileDto>`。
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

/** 在 feature 内 provide [ProfileApi]，复用 NetworkModule 注入的 [Retrofit] 单例。 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileNetworkModule {

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)
}
