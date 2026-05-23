package com.pythontopia.feature.workshop

/**
 * Workshop（代码工坊）UI 单一来源。
 *
 * 屏幕的 3 段布局直接读取这里：
 *   - 顶部：[currentProblem] + [problems] + [isPickerOpen]
 *   - 中部：[userCode] + [isSubmitting]
 *   - 底部：[latestStatus] + [history] + [errorMessage]
 *
 * 设计原则（ADR-001 §1 决策 10）：
 *   - 所有状态由 ViewModel 单向写入；
 *   - Compose 端只在 collectAsStateWithLifecycle 读取，不持有本地状态拷贝；
 *   - terminal vs polling 通过 [latestStatus]?.isTerminal 推导，避免双源真相。
 */
data class WorkshopUiState(
    val kcCodeFilter: String = DEFAULT_KC_CODE,
    val problems: List<ProblemDto> = emptyList(),
    val isLoadingProblems: Boolean = false,
    val currentProblem: ProblemDto? = null,
    val userCode: String = DEFAULT_STARTER_CODE,
    val isPickerOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val isPolling: Boolean = false,
    val currentSubmissionId: Long? = null,
    val latestStatus: SubmissionDto? = null,
    val history: List<SubmissionDto> = emptyList(),
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = currentProblem != null &&
            userCode.isNotBlank() &&
            !isSubmitting &&
            !isPolling

    companion object {
        const val DEFAULT_KC_CODE: String = "PY43-KC-016"
        const val DEFAULT_STARTER_CODE: String = "# 在这里编写你的解答\n"
    }
}
