package com.pythontopia.feature.retro

/**
 * 复盘大厅 UI 状态；`error` 用 String 避免与 AppError 耦合。
 * 不用 sealed wrapper，以便主报告与历史列表分块刷新。
 */
data class RetroUiState(
    val loading: Boolean = false,
    val currentReport: RetroReport? = null,
    val pastReports: List<RetroReport> = emptyList(),
    val thisWeekIndex: Long? = null,
    val error: String? = null,
)
