package com.pythontopia.feature.teacherreview

/**
 * 教师审题 UI 模型。对齐 Web `pages/teacher/TeacherReviewPage.vue`：
 * 调 boss 出题端点拉 AI/golden 题（`reviewed` = 5 重审查通过），教师本地「上线/驳回」。
 *
 * 审核决定为会话本地（Web 用 localStorage 兜底；后端无审核决定持久化端点）。
 * Web 驳回会best-effort 上报 `LearningEvent('AI_PROBLEM_REJECTED')`，但该端点 student-only，
 * 教师调用必 403（Web 静默吞掉）——故 Android 端不发该必失败请求，决定仅本地记录。
 */

enum class ReviewDecision { PENDING, APPROVED, REJECTED }

/** 一道待审题（boss 出题端点产物，UI 视图模型）。 */
data class ReviewProblem(
    val problemId: String,
    val chapter: Int,
    val stage: Int,
    val difficulty: Int,
    val kcCode: String?,
    val statement: String,
    val hints: List<String>,
    val sampleInput: String?,
    val sampleOutput: String?,
    val totalTestCases: Int,
    val reviewed: Boolean,
    val aiGenerated: Boolean,
)

/** 审核队列条目：题目 + 教师决定（含驳回原因）。[key] 用于列表稳定标识。 */
data class ReviewEntry(
    val problem: ReviewProblem,
    val fetchedAtMs: Long,
    val decision: ReviewDecision = ReviewDecision.PENDING,
    val reason: String? = null,
) {
    val key: String get() = "${problem.problemId}-$fetchedAtMs"
}
