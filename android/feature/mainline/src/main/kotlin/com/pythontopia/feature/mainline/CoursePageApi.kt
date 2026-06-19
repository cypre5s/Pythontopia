package com.pythontopia.feature.mainline

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

/** 课件页（`GET /api/course/pages?kcCode=`）：主线「学」逐页阅读用，含 contentMarkdown 原文。 */
@Serializable
data class StageCoursePageDto(
    val id: Long,
    val pageNo: Int = 0,
    val title: String? = null,
    val contentMarkdown: String? = null,
    val excerpt: String? = null,
)

interface CoursePageApi {
    @GET("api/course/pages")
    suspend fun pages(@Query("kcCode") kcCode: String): List<StageCoursePageDto>
}

@Module
@InstallIn(SingletonComponent::class)
object CoursePageNetworkModule {

    @Provides
    @Singleton
    fun provideCoursePageApi(retrofit: Retrofit): CoursePageApi =
        retrofit.create(CoursePageApi::class.java)
}
