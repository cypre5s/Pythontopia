package com.pythontopia.feature.workshop

/**
 * 代码工坊 UI 单一状态源；终态与轮询中由 [latestStatus]?.isTerminal 推导，避免双源真相。
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
