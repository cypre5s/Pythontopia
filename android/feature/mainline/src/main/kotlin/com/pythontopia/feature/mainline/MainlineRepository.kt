package com.pythontopia.feature.mainline

import com.pythontopia.core.data.dto.mainline.ChapterTestPaperDto
import com.pythontopia.core.data.dto.mainline.ChapterTestResultDto
import com.pythontopia.core.data.dto.mainline.ChapterTestSubmitRequest
import com.pythontopia.core.data.dto.mainline.ExampleDoneRequest
import com.pythontopia.core.data.dto.mainline.MainlineOverviewDto
import com.pythontopia.core.data.dto.mainline.PagesReadRequest
import com.pythontopia.core.data.dto.mainline.StageDetailDto
import com.pythontopia.core.data.dto.mainline.StageProgressDto
import com.pythontopia.core.data.dto.mainline.StarAwardResultDto
import com.pythontopia.core.data.dto.mainline.TestPrepareResponse
import com.pythontopia.core.data.dto.mainline.VideoProgressRequest
import com.pythontopia.core.network.api.MainlineApi
import com.pythontopia.core.network.api.MainlineTestApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R-15 主线仓库：身份由 JWT 界定。进度（看/学/练）只由后端 evidence 推进，
 * 客户端仅做打点上报与读取，不本地伪造完成态。
 */
@Singleton
class MainlineRepository @Inject constructor(
    private val api: MainlineApi,
    private val testApi: MainlineTestApi,
) {

    suspend fun overview(): MainlineOverviewDto = api.overview()

    suspend fun stageDetail(stageId: Long): StageDetailDto = api.stageDetail(stageId)

    /** 「看」打点：上报本次可见时长增量（秒）。 */
    suspend fun reportVideoProgress(stageId: Long, deltaSec: Int): StageProgressDto =
        api.reportVideoProgress(stageId, VideoProgressRequest(deltaSec))

    /** 「学」打点：批量上报已读课件页（幂等去重）。 */
    suspend fun reportPagesRead(stageId: Long, pageIds: List<Long>): StageProgressDto =
        api.reportPagesRead(stageId, PagesReadRequest(pageIds))

    /** 「练」打点：例题完成（带 submissionId 时后端校验本人且 PASSED）。 */
    suspend fun reportExampleDone(
        stageId: Long,
        problemId: Long,
        submissionId: Long? = null,
    ): StageProgressDto =
        api.reportExampleDone(stageId, ExampleDoneRequest(problemId, submissionId))

    /** 章末测试：触发异步预生成（考试期零 LLM）。 */
    suspend fun prepareTest(chapter: Int): TestPrepareResponse = testApi.prepareTest(chapter)

    /** 章末测试：开考，下发去答案题面。 */
    suspend fun startTest(chapter: Int): ChapterTestPaperDto = testApi.startTest(chapter)

    /** 章末测试：交卷判分（服务端比对，编程走判题沙箱）。 */
    suspend fun submitTest(
        chapter: Int,
        testId: Long,
        answers: Map<String, String>,
    ): ChapterTestResultDto =
        testApi.submitTest(chapter, testId, ChapterTestSubmitRequest(answers))

    /** 章末测试：最近一次结果（解析仅提交后返回）。 */
    suspend fun latestTest(chapter: Int): ChapterTestResultDto = testApi.latestTest(chapter)

    /** 错题复盘补星：校验本章 Bug 矿洞 RESOLVED ≥ 3，幂等、只升不降、每途径每章至多 +1。 */
    suspend fun claimErrorbookRecovery(chapter: Int): StarAwardResultDto =
        api.recoveryErrorbook(chapter)
}
