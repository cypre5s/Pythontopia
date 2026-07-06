package com.pythontopia.feature.problems

/**
 * 题库浏览 UI 模型；Retrofit wire 类型留在 [ProblemsRepository] 内部。
 *
 * 后端真实端点（零 fixture，对齐 Web 题库语义）：
 * - `GET /api/course/problems`（可选 `?kcCode=`）→ 题目列表（packId 43）
 * - `GET /api/course/kcs` → KC 名称与章节序（用于分组/显示）
 * - `GET /api/submissions?limit=` → 聚合玩家状态（NOT_TRIED / PASSED / FAILED）+ 尝试次数
 */

enum class ProblemStatus { NOT_TRIED, PASSED, FAILED }

enum class ProblemDifficulty(val canonical: String, val label: String) {
    LOW("Low", "简单"),
    MEDIUM("Medium", "中等"),
    HIGH("High", "困难"),
    UNKNOWN("", "未知");

    companion object {
        fun fromBackend(raw: String?): ProblemDifficulty = when (raw?.trim()?.lowercase()) {
            "low", "easy" -> LOW
            "medium", "mid" -> MEDIUM
            "high", "hard" -> HIGH
            else -> UNKNOWN
        }
    }
}

enum class ProblemSort(val label: String) {
    CHAPTER("按章节"),
    DIFFICULTY("按难度"),
    ATTEMPTS("按尝试"),
}

/** 一行题目（题面随行展开，不跳转 arena）。 */
data class ProblemRow(
    val problemId: Long,
    val title: String,
    val kcCode: String,
    val kcName: String,
    val chapterOrder: Int?,
    val difficulty: ProblemDifficulty,
    val statementMarkdown: String,
    val inputDescription: String,
    val outputDescription: String,
    val sourceLabel: String,
    val status: ProblemStatus,
    val attempts: Int,
)
