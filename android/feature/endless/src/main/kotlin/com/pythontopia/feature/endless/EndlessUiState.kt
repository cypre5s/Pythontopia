package com.pythontopia.feature.endless

data class EndlessUiState(
    val loadingProblem: Boolean = true,
    val problem: EndlessProblemDto? = null,
    val code: String = STARTER_CODE,
    val submitting: Boolean = false,
    val liveStatus: EndlessStatus? = null,
    val result: EndlessSubmissionDto? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val solved: Int = 0,
    val attempts: Int = 0,
    val score: Int = 0,
    val error: String? = null,
) {
    val solvedThisProblem: Boolean get() = result?.status == EndlessStatus.PASSED

    companion object {
        const val STARTER_CODE = "# 无尽练习：写下你的 Python 解法\n"
    }
}
