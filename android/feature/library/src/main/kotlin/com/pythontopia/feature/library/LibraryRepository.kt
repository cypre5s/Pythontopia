package com.pythontopia.feature.library

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

/** 课程图书馆 Retrofit 端点（只读浏览课件）。 */
interface LibraryApi {

    @GET("api/course/chapters")
    suspend fun chapters(): List<LibraryChapterDto>

    @GET("api/course/kcs")
    suspend fun kcs(@Query("chapterId") chapterId: Long): List<LibraryKcDto>

    @GET("api/course/pages")
    suspend fun pages(@Query("kcCode") kcCode: String): List<LibraryPageDto>
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
}

@Module
@InstallIn(SingletonComponent::class)
object LibraryNetworkModule {

    @Provides
    @Singleton
    fun provideLibraryApi(retrofit: Retrofit): LibraryApi =
        retrofit.create(LibraryApi::class.java)
}
