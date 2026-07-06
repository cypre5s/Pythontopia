package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.mainline.EndlessSummaryRequest
import com.pythontopia.core.data.dto.mainline.ExampleDoneRequest
import com.pythontopia.core.data.dto.mainline.MainlineOverviewDto
import com.pythontopia.core.data.dto.mainline.PageReadRequest
import com.pythontopia.core.data.dto.mainline.PagesReadRequest
import com.pythontopia.core.data.dto.mainline.PracticeAnswerRequest
import com.pythontopia.core.data.dto.mainline.PracticeAnswerResultDto
import com.pythontopia.core.data.dto.mainline.StageDetailDto
import com.pythontopia.core.data.dto.mainline.StageProgressDto
import com.pythontopia.core.data.dto.mainline.StarAwardResultDto
import com.pythontopia.core.data.dto.mainline.VideoProgressRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * R-15 主线学习闭环 API（全部 STUDENT + Bearer）：冒险地图总览 + 小节三件套（看/学/练）打点 +
 * 客观题判分 + 补星。章末测试见 [MainlineTestApi]。
 */
interface MainlineApi {

    @GET("api/mainline/overview")
    suspend fun overview(): MainlineOverviewDto

    @GET("api/mainline/stages/{stageId}")
    suspend fun stageDetail(@Path("stageId") stageId: Long): StageDetailDto

    @POST("api/mainline/stages/{stageId}/video-progress")
    suspend fun reportVideoProgress(
        @Path("stageId") stageId: Long,
        @Body body: VideoProgressRequest,
    ): StageProgressDto

    @POST("api/mainline/stages/{stageId}/page-read")
    suspend fun reportPageRead(
        @Path("stageId") stageId: Long,
        @Body body: PageReadRequest,
    ): StageProgressDto

    @POST("api/mainline/stages/{stageId}/pages-read")
    suspend fun reportPagesRead(
        @Path("stageId") stageId: Long,
        @Body body: PagesReadRequest,
    ): StageProgressDto

    @POST("api/mainline/stages/{stageId}/example-done")
    suspend fun reportExampleDone(
        @Path("stageId") stageId: Long,
        @Body body: ExampleDoneRequest,
    ): StageProgressDto

    @POST("api/mainline/stages/{stageId}/practice/{questionId}/answer")
    suspend fun answerPractice(
        @Path("stageId") stageId: Long,
        @Path("questionId") questionId: Long,
        @Body body: PracticeAnswerRequest,
    ): PracticeAnswerResultDto

    @POST("api/mainline/endless/summary")
    suspend fun endlessSummary(@Body body: EndlessSummaryRequest): StarAwardResultDto

    @POST("api/mainline/chapters/{chapter}/recovery/errorbook")
    suspend fun recoveryErrorbook(@Path("chapter") chapter: Int): StarAwardResultDto
}
