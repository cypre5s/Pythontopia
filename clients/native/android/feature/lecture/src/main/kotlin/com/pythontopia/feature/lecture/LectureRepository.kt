package com.pythontopia.feature.lecture

import com.pythontopia.core.network.BaseUrlProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** `GET /api/workshop/resources` 列表/详情；`GET /api/course/videos` 章节视频清单。 */
interface LectureApi {

    @GET("api/workshop/resources")
    suspend fun list(
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 50,
    ): List<LectureListItemDto>

    @GET("api/workshop/resources/{id}")
    suspend fun findOne(@Path("id") id: Long): LectureResourceDto

    @GET("api/course/videos")
    suspend fun videos(@Query("chapterId") chapterId: Int): List<CourseVideoDto>
}

/** HANDOUT 列表失败静默空表；详情失败上抛 [Result]；多态 content 宽松解码为 [HandoutContent]。 */
@Singleton
class LectureRepository @Inject constructor(
    private val api: LectureApi,
    private val baseUrlProvider: BaseUrlProvider,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun listHandouts(): List<LectureListItemDto> =
        runCatching { api.list(type = "HANDOUT") }.getOrElse { emptyList() }

    suspend fun findOne(id: Long): Result<LectureResourceDto> =
        runCatching { api.findOne(id) }

    /** 把资源 content 解码为讲义结构；非 handout 或结构不符则返回 null。 */
    fun parseHandout(content: JsonElement?): HandoutContent? =
        content
            ?.let { runCatching { json.decodeFromJsonElement(HandoutContent.serializer(), it) }.getOrNull() }
            ?.takeIf { it.sections.isNotEmpty() }

    /** 某章视频清单；失败/无视频回退空表（线下课无视频，UI 回退讲义）。 */
    suspend fun chapterVideos(chapter: Int): List<CourseVideoDto> =
        runCatching { api.videos(chapter) }.getOrElse { emptyList() }

    /** 视频字节流绝对地址（匿名 + Range，供 [com.pythontopia.core.design.PytVideoPlayer] 直连）。 */
    fun videoUrl(id: Long): String =
        baseUrlProvider.current().trimEnd('/') + "/api/course/videos/$id"
}

@Module
@InstallIn(SingletonComponent::class)
object LectureNetworkModule {

    @Provides
    @Singleton
    fun provideLectureApi(retrofit: Retrofit): LectureApi =
        retrofit.create(LectureApi::class.java)
}
