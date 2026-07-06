package com.pythontopia.feature.boss

enum class BossPhase { PICK_CHAPTER, BATTLE, VICTORY }

/** BOSS 战 UI 状态；[clearedStages] = stage-1，[isLastStage] 当 stage ≥ [TOTAL_STAGES]。 */
data class BossUiState(
    val phase: BossPhase = BossPhase.PICK_CHAPTER,
    val chapter: Int = 0,
    val stage: Int = 1,
    val problem: BossProblemDto? = null,
    val code: String = STARTER_CODE,
    val result: BossAttemptResultDto? = null,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val startEpochMs: Long = 0L,
) {
    val clearedStages: Int get() = stage - 1
    val isLastStage: Boolean get() = stage >= TOTAL_STAGES

    companion object {
        const val TOTAL_STAGES = 4
        const val STARTER_CODE = "# 在这里写下你的 Python 解法\n"
    }
}
