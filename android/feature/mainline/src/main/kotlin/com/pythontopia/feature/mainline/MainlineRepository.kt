package com.pythontopia.feature.mainline

import com.pythontopia.core.data.dto.mainline.ChapterTestPaperDto
import com.pythontopia.core.data.dto.mainline.ChapterTestResultDto
import com.pythontopia.core.data.dto.mainline.ChapterTestSubmitRequest
import com.pythontopia.core.data.dto.mainline.ExampleDoneRequest
import com.pythontopia.core.data.dto.mainline.MainlineOverviewDto
import com.pythontopia.core.data.dto.mainline.PageReadRequest
import com.pythontopia.core.data.dto.mainline.PagesReadRequest
import com.pythontopia.core.data.dto.mainline.PracticeAnswerRequest
import com.pythontopia.core.data.dto.mainline.PracticeAnswerResultDto
import com.pythontopia.core.data.dto.mainline.StageDetailDto
import com.pythontopia.core.data.dto.mainline.StageProgressDto
import com.pythontopia.core.data.dto.mainline.StarAwardResultDto
import com.pythontopia.core.data.dto.mainline.TestPrepareResponse
import com.pythontopia.core.data.dto.mainline.VideoProgressRequest
import com.pythontopia.core.network.BaseUrlProvider
import com.pythontopia.core.network.api.MainlineApi
import com.pythontopia.core.network.api.MainlineTestApi
import javax.inject.Inject
import javax.inject.Singleton

/** 章末测评模式：TEST=正式大测试，QUIZ=快测；二者同 DTO/流程，仅后端路径段不同。 */
enum class ChapterAssessment { TEST, QUIZ }

/**
 * R-15 主线仓库：身份由 JWT 界定。进度（看/学/练）只由后端 evidence 推进，
 * 客户端仅做打点上报与读取，不本地伪造完成态。
 */
@Singleton
class MainlineRepository @Inject constructor(
    private val api: MainlineApi,
    private val testApi: MainlineTestApi,
    private val coursePageApi: CoursePageApi,
    private val baseUrlProvider: BaseUrlProvider,
) {

    /** 视频字节流绝对地址（匿名 + Range，供 PytVideoPlayer 直连）。 */
    fun videoUrl(id: Long): String =
        baseUrlProvider.current().trimEnd('/') + "/api/course/videos/$id"

    suspend fun overview(): MainlineOverviewDto = api.overview()

    suspend fun stageDetail(stageId: Long): StageDetailDto = api.stageDetail(stageId)

    /** 「学」逐页阅读：拉本小节各 KC 的课件页（按 id 去重、按页码排序）。失败回退空表。 */
    suspend fun coursePagesForKcs(kcCodes: List<String>): List<StageCoursePageDto> {
        val byId = LinkedHashMap<Long, StageCoursePageDto>()
        for (kc in kcCodes) {
            runCatching { coursePageApi.pages(kc) }.getOrNull()?.forEach { byId.putIfAbsent(it.id, it) }
        }
        return byId.values.sortedBy { it.pageNo }
    }

    /** 「学」单页阅读打点（幂等）。 */
    suspend fun reportPageRead(stageId: Long, pageId: Long): StageProgressDto =
        api.reportPageRead(stageId, PageReadRequest(pageId))

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

    /** 「练」客观题判分：服务端比对（不泄题），返回 correct + 正确答案 + 解析。 */
    suspend fun answerPracticeQuestion(
        stageId: Long,
        questionId: Long,
        answers: List<String>,
    ): PracticeAnswerResultDto =
        api.answerPractice(stageId, questionId, PracticeAnswerRequest(answers))

    /** 章末测评：触发异步预生成（考试期零 LLM）。[assessment] 区分大测试/快测。 */
    suspend fun prepareTest(
        chapter: Int,
        assessment: ChapterAssessment = ChapterAssessment.TEST,
    ): TestPrepareResponse =
        if (assessment == ChapterAssessment.QUIZ) testApi.prepareQuiz(chapter) else testApi.prepareTest(chapter)

    /** 章末测评：开考，下发去答案题面。 */
    suspend fun startTest(
        chapter: Int,
        assessment: ChapterAssessment = ChapterAssessment.TEST,
    ): ChapterTestPaperDto =
        if (assessment == ChapterAssessment.QUIZ) testApi.startQuiz(chapter) else testApi.startTest(chapter)

    /** 章末测评：交卷判分（服务端比对，编程走判题沙箱）。 */
    suspend fun submitTest(
        chapter: Int,
        testId: Long,
        answers: Map<String, String>,
        assessment: ChapterAssessment = ChapterAssessment.TEST,
    ): ChapterTestResultDto {
        val body = ChapterTestSubmitRequest(answers)
        return if (assessment == ChapterAssessment.QUIZ) {
            testApi.submitQuiz(chapter, testId, body)
        } else {
            testApi.submitTest(chapter, testId, body)
        }
    }

    /** 章末测评：最近一次结果（解析仅提交后返回）。 */
    suspend fun latestTest(
        chapter: Int,
        assessment: ChapterAssessment = ChapterAssessment.TEST,
    ): ChapterTestResultDto =
        if (assessment == ChapterAssessment.QUIZ) testApi.latestQuiz(chapter) else testApi.latestTest(chapter)

    /** 错题复盘补星：校验本章 Bug 矿洞 RESOLVED ≥ 3，幂等、只升不降、每途径每章至多 +1。 */
    suspend fun claimErrorbookRecovery(chapter: Int): StarAwardResultDto =
        api.recoveryErrorbook(chapter)
}
