package com.pythontopia.feature.workshop

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

/** `POST /api/intent/route` 单句意图分类（非流式），仅学生。 */
interface IntentApi {

    @POST("api/intent/route")
    suspend fun route(@Body body: IntentRouteRequestBody): IntentRouteResponse
}

/**
 * 意图路由仓库：把用户一句话送后端分类。失败向上游抛出（不静默吞），由 ViewModel 写入错误态。
 * 空集合参数在发送前剔除，避免发出无意义的空数组。
 */
@Singleton
class IntentRepository @Inject constructor(
    private val api: IntentApi,
) {

    suspend fun route(
        message: String,
        manualKcCodes: List<String>? = null,
        recentKcCodes: List<String>? = null,
        courseKcs: List<IntentCourseKc>? = null,
        currentChapter: Int? = null,
    ): IntentRouteResponse =
        api.route(
            IntentRouteRequestBody(
                message = message,
                manualKcCodes = manualKcCodes?.takeIf { it.isNotEmpty() },
                courseKcs = courseKcs?.takeIf { it.isNotEmpty() },
                currentChapter = currentChapter,
                recentKcCodes = recentKcCodes?.takeIf { it.isNotEmpty() },
            ),
        )
}

@Module
@InstallIn(SingletonComponent::class)
object IntentNetworkModule {

    @Provides
    @Singleton
    fun provideIntentApi(retrofit: Retrofit): IntentApi =
        retrofit.create(IntentApi::class.java)
}
