package com.pythontopia.feature.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** 课程图书馆 Retrofit 端点（只读浏览课件 + 课件问答 RAG + 课件页 PNG 渲染图）。 */
interface LibraryApi {

    @GET("api/course/chapters")
    suspend fun chapters(): List<LibraryChapterDto>

    @GET("api/course/kcs")
    suspend fun kcs(@Query("chapterId") chapterId: Long): List<LibraryKcDto>

    @GET("api/course/pages")
    suspend fun pages(@Query("kcCode") kcCode: String): List<LibraryPageDto>

    @POST("api/courseware/answer")
    suspend fun answer(@Body body: CoursewareAnswerRequest): CoursewareAnswerResponseDto

    @Streaming
    @GET("api/course/pages/{pageId}/image")
    suspend fun pageImage(@Path("pageId") pageId: Long): ResponseBody
}

/**
 * 课程图书馆仓库：章 → KC → 页 三级钻取。
 * 网络异常静默兜底空列表（只读浏览，失败不致命；UI 用空态提示）。
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val api: LibraryApi,
) {
    suspend fun chapters(): List<LibraryChapterDto> =
        runCatching { api.chapters() }.getOrElse { emptyList() }

    suspend fun kcs(chapterId: Long): List<LibraryKcDto> =
        runCatching { api.kcs(chapterId) }.getOrElse { emptyList() }

    suspend fun pages(kcCode: String): List<LibraryPageDto> =
        runCatching { api.pages(kcCode) }.getOrElse { emptyList() }

    /** 课件问答：LLM 依据命中课件作答 + 页码引用；失败上抛 [Result]（与只读浏览不同，需提示用户）。 */
    suspend fun answerCourseware(query: String, kcCodes: List<String>?): Result<CoursewareAnswerResponseDto> =
        runCatching { api.answer(CoursewareAnswerRequest(query = query, kcCodes = kcCodes?.takeIf { it.isNotEmpty() })) }

    /** 课件页 PNG 渲染图（后端 PDFBox 渲染原版 PDF 对应页）；IO 线程解码，失败回退 null（UI 降级到文本）。 */
    suspend fun pageImage(pageId: Long): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { api.pageImage(pageId).byteStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LibraryNetworkModule {

    @Provides
    @Singleton
    fun provideLibraryApi(retrofit: Retrofit): LibraryApi =
        retrofit.create(LibraryApi::class.java)
}
