package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.mainline.ChapterTestPaperDto
import com.pythontopia.core.data.dto.mainline.ChapterTestResultDto
import com.pythontopia.core.data.dto.mainline.ChapterTestSubmitRequest
import com.pythontopia.core.data.dto.mainline.TestPrepareResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * R-15 章末大测试 API（STUDENT + Bearer）：prepare（异步预生成）→ start（去答案发卷）→
 * submit（服务端判分）→ latest（最近结果，解析仅提交后返回）。考试期零 LLM。
 */
interface MainlineTestApi {

    @POST("api/mainline/chapters/{chapter}/test/prepare")
    suspend fun prepareTest(@Path("chapter") chapter: Int): TestPrepareResponse

    @POST("api/mainline/chapters/{chapter}/test/start")
    suspend fun startTest(@Path("chapter") chapter: Int): ChapterTestPaperDto

    @POST("api/mainline/chapters/{chapter}/test/{testId}/submit")
    suspend fun submitTest(
        @Path("chapter") chapter: Int,
        @Path("testId") testId: Long,
        @Body body: ChapterTestSubmitRequest,
    ): ChapterTestResultDto

    @GET("api/mainline/chapters/{chapter}/test/latest")
    suspend fun latestTest(@Path("chapter") chapter: Int): ChapterTestResultDto

    // 章末快测（quiz）：与 test 同 DTO/流程，仅路径段为 /quiz（对齐后端 ChapterQuizController）。
    @POST("api/mainline/chapters/{chapter}/quiz/prepare")
    suspend fun prepareQuiz(@Path("chapter") chapter: Int): TestPrepareResponse

    @POST("api/mainline/chapters/{chapter}/quiz/start")
    suspend fun startQuiz(@Path("chapter") chapter: Int): ChapterTestPaperDto

    @POST("api/mainline/chapters/{chapter}/quiz/{testId}/submit")
    suspend fun submitQuiz(
        @Path("chapter") chapter: Int,
        @Path("testId") testId: Long,
        @Body body: ChapterTestSubmitRequest,
    ): ChapterTestResultDto

    @GET("api/mainline/chapters/{chapter}/quiz/latest")
    suspend fun latestQuiz(@Path("chapter") chapter: Int): ChapterTestResultDto
}
