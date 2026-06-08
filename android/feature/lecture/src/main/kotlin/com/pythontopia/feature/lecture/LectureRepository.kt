package com.pythontopia.feature.lecture

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

/** `GET /api/workshop/resources` 列表；`GET /api/workshop/resources/{id}` 详情。 */
interface LectureApi {

    @GET("api/workshop/resources")
    suspend fun list(
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 50,
    ): List<LectureListItemDto>

    @GET("api/workshop/resources/{id}")
    suspend fun findOne(@Path("id") id: Long): LectureResourceDto
}

/** HANDOUT 列表失败静默空表；详情失败上抛 [Result]；多态 content 宽松解码为 [HandoutContent]。 */
@Singleton
class LectureRepository @Inject constructor(
    private val api: LectureApi,
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
}

@Module
@InstallIn(SingletonComponent::class)
object LectureNetworkModule {

    @Provides
    @Singleton
    fun provideLectureApi(retrofit: Retrofit): LectureApi =
        retrofit.create(LectureApi::class.java)
}
