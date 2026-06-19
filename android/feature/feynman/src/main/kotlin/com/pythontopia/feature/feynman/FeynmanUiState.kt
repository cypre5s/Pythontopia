package com.pythontopia.feature.feynman

/**
 * 费曼反演 UI 状态，对齐 Web `stores/feynman.ts`：
 * 误解来自会话 [currentSession]；[latestProducedCode] / [latestJudgeResult] 端侧推导累积。
 */
data class FeynmanUiState(
    val kcCode: String = "",
    val activeKc: String = "",
    val currentSession: FeynmanSessionDto? = null,
    val turns: List<FeynmanTurnDto> = emptyList(),
    val loading: Boolean = false,
    val teaching: Boolean = false,
    val judging: Boolean = false,
    val error: String? = null,
    val latestProducedCode: String = "",
    val latestJudgeResult: FeynmanJudgeResultDto? = null,
    /** 代码找茬小游戏状态（移植 Web BugHunterGame）。 */
    val bugHunt: BugHunterState = BugHunterState(),
) {
    val misconceptions: List<FeynmanMisconceptionDto>
        get() = currentSession?.tuteeState?.misconceptions ?: emptyList()

    /** 玻璃盒证据链：误解 + 判题命中的真实 event_id 去重排序。 */
    val evidenceEventIds: List<Long>
        get() {
            val ids = sortedSetOf<Long>()
            misconceptions.forEach { ids.addAll(it.evidenceEventIds) }
            latestJudgeResult?.let { ids.addAll(it.evidenceEventIds) }
            return ids.toList()
        }

    val canTeach: Boolean get() = currentSession != null && !teaching
    val canJudge: Boolean
        get() = currentSession != null && latestProducedCode.isNotBlank() && !judging
}

enum class BugHuntPhase { IDLE, PLAYING, DONE }

/** 代码找茬一局结算。 */
data class BugHuntResult(
    val hits: Int,
    val wrong: Int,
    val missed: Int,
    val perfect: Boolean,
    val comboBonus: Int,
)

/** 代码找茬游戏态：30 秒限时点出 bug 行，命中/误报/漏判即时判分（客户端计分）。 */
data class BugHunterState(
    val phase: BugHuntPhase = BugHuntPhase.IDLE,
    val loading: Boolean = false,
    val lines: List<String> = emptyList(),
    val bugLines: Set<Int> = emptySet(),
    val hintMd: String = "",
    val source: String = "none",
    val selected: Set<Int> = emptySet(),
    val secondsLeft: Int = ROUND_SECONDS,
    val score: Int = 0,
    val streak: Int = 0,
    val best: Int = 0,
    val result: BugHuntResult? = null,
) {
    val playable: Boolean get() = lines.isNotEmpty()
    val sourceLabel: String
        get() = when (source) {
            "real_misconception" -> "你的同款错"
            "generic_beginner_bug" -> "常见陷阱"
            else -> ""
        }

    companion object {
        const val ROUND_SECONDS = 30
        const val HIT_SCORE = 10
        const val WRONG_PENALTY = 4
        const val COMBO_BONUS = 5
    }
}
